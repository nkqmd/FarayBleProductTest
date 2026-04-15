import hashlib
import json
import os
from datetime import datetime, timedelta, timezone

from flask import Flask, jsonify, request, send_from_directory, Response
from flask_cors import CORS
from flask_jwt_extended import (
    JWTManager,
    create_access_token,
    create_refresh_token,
    get_jwt,
    get_jwt_identity,
    jwt_required,
)
from werkzeug.utils import secure_filename

app = Flask(__name__)
app.config["SECRET_KEY"] = "your-secret-key"
app.config["JWT_SECRET_KEY"] = "jwt-secret-key"
app.config["JWT_ACCESS_TOKEN_EXPIRES"] = timedelta(hours=24)
app.config["JWT_REFRESH_TOKEN_EXPIRES"] = timedelta(days=7)
app.config["UPLOAD_FOLDER"] = "uploads"
app.config["MAX_CONTENT_LENGTH"] = 16 * 1024 * 1024

print(app.config.get("JWT_ACCESS_TOKEN_EXPIRES"))
print(app.config.get("JWT_REFRESH_TOKEN_EXPIRES"))

CORS(app)
jwt = JWTManager(app)

# Demo-only in-memory storage. Restarting the process clears all state.
token_blocklist = set()
user_active_tokens = {}

BATCH_ROOT = os.path.join(app.root_path, "batch")

os.makedirs(app.config["UPLOAD_FOLDER"], exist_ok=True)
os.makedirs(BATCH_ROOT, exist_ok=True)

users = {
    "test": "123456",
}


def get_expire_seconds(config_key):
    return int(app.config[config_key].total_seconds())


def get_safe_batch_id(batch_id):
    safe_batch_id = secure_filename(batch_id)
    if not safe_batch_id or safe_batch_id != batch_id:
        return None
    return safe_batch_id


def get_batch_directory_path(batch_id):
    safe_batch_id = get_safe_batch_id(batch_id)
    if not safe_batch_id:
        return None
    return os.path.join(BATCH_ROOT, safe_batch_id)


def get_batch_file_path(batch_id):
    batch_dir = get_batch_directory_path(batch_id)
    if not batch_dir:
        return None
    safe_batch_id = get_safe_batch_id(batch_id)
    return os.path.join(batch_dir, f"{safe_batch_id}_mac_list.txt")


def get_batch_config_file_path(batch_id):
    batch_dir = get_batch_directory_path(batch_id)
    if not batch_dir:
        return None
    safe_batch_id = get_safe_batch_id(batch_id)
    return os.path.join(batch_dir, f"{safe_batch_id}_config.json")


def load_batch_resources(batch_id):
    batch_dir = get_batch_directory_path(batch_id)
    mac_file_path = get_batch_file_path(batch_id)
    config_path = get_batch_config_file_path(batch_id)

    if not batch_dir or not mac_file_path or not config_path:
        return None, "Invalid batch_id"
    if not os.path.isdir(batch_dir):
        return None, f"Batch directory not found: batch/{batch_id}"
    if not os.path.exists(mac_file_path):
        return None, f"Batch MAC file not found: batch/{batch_id}/{os.path.basename(mac_file_path)}"
    if not os.path.exists(config_path):
        return None, f"Batch config file not found: batch/{batch_id}/{os.path.basename(config_path)}"

    with open(mac_file_path, "r", encoding="utf-8") as batch_file:
        mac_list = [line.strip() for line in batch_file if line.strip()]

    with open(config_path, "r", encoding="utf-8") as config_file:
        config_data = json.load(config_file)

    required_keys = {
        "expected_count",
        "expire_time",
        "expected_firmware",
        "device_type",
        "ble_name_prefix",
        "ble_config",
    }
    missing_keys = sorted(required_keys - set(config_data.keys()))
    if missing_keys:
        return None, (
            f"Missing required keys in batch/{batch_id}/{os.path.basename(config_path)}: {', '.join(missing_keys)}"
        )

    ble_name_prefix = config_data.get("ble_name_prefix")
    if not isinstance(ble_name_prefix, str) or not ble_name_prefix:
        return None, f"Invalid ble_name_prefix in batch/{batch_id}/{os.path.basename(config_path)}"
    if ble_name_prefix != ble_name_prefix.strip() or any(char.isspace() for char in ble_name_prefix):
        return None, f"ble_name_prefix must not contain whitespace in batch/{batch_id}/{os.path.basename(config_path)}"
    if not ble_name_prefix.endswith("-"):
        return None, f"ble_name_prefix must end with '-' in batch/{batch_id}/{os.path.basename(config_path)}"

    return {
        "batch_id": batch_id,
        "batch_dir": batch_dir,
        "mac_file_path": mac_file_path,
        "config_path": config_path,
        "mac_list": mac_list,
        "config": config_data,
    }, None


def get_batch_error_status(error_message):
    return 404 if "not found" in error_message.lower() else 400


def decode_token_jti(token):
    decoded_token = jwt._decode_jwt_from_config(token, csrf_value=None, allow_expired=True)
    return decoded_token["jti"]


def revoke_token_by_jti(jti):
    if jti:
        token_blocklist.add(jti)


def revoke_user_active_tokens(username):
    active_tokens = user_active_tokens.get(username, {})
    revoke_token_by_jti(active_tokens.get("access_jti"))
    revoke_token_by_jti(active_tokens.get("refresh_jti"))


def remember_user_tokens(username, access_token, refresh_token):
    user_active_tokens[username] = {
        "access_jti": decode_token_jti(access_token),
        "refresh_jti": decode_token_jti(refresh_token),
    }


def log_current_token(action):
    jwt_data = get_jwt()
    expires_at = datetime.fromtimestamp(jwt_data["exp"], tz=timezone.utc).isoformat()
    print(
        f"[JWT] action={action} sub={jwt_data.get('sub')} "
        f"type={jwt_data.get('type')} jti={jwt_data.get('jti')} exp={expires_at}"
    )


def get_batch_result_folder(batch_id):
    batch_dir = get_batch_directory_path(batch_id)
    if not batch_dir:
        return None
    return os.path.join(batch_dir, "result")


def ensure_batch_result_folder(batch_id):
    result_folder = get_batch_result_folder(batch_id)
    if not result_folder:
        return None
    os.makedirs(result_folder, exist_ok=True)
    return result_folder


def resolve_upload_batch_id():
    return (
        (request.form.get("batch_id") or "").strip()
        or (request.args.get("batch_id") or "").strip()
    )


def list_relative_files(root_dir):
    files = []
    for current_root, _, file_names in os.walk(root_dir):
        for file_name in file_names:
            full_path = os.path.join(current_root, file_name)
            files.append(os.path.relpath(full_path, root_dir).replace("\\", "/"))
    return sorted(files)


def build_mac_list_hash(mac_list):
    payload = "\n".join(mac_list).encode("utf-8")
    return f"sha256:{hashlib.sha256(payload).hexdigest()}"


def build_production_summary_data(resources):
    batch_id = resources["batch_id"]
    mac_list = resources["mac_list"]
    batch_config = resources["config"]
    return {
        "batch_id": batch_id,
        "expected_count": len(mac_list),
        "expire_time": batch_config["expire_time"],
        "expected_firmware": batch_config["expected_firmware"],
        "device_type": batch_config["device_type"],
        "ble_name_prefix": batch_config["ble_name_prefix"],
        "ble_config": batch_config["ble_config"],
        "mac_list_count": len(mac_list),
        "mac_list_hash": build_mac_list_hash(mac_list),
        "mac_list_version": datetime.fromtimestamp(
            os.path.getmtime(resources["mac_file_path"]),
            tz=timezone.utc,
        ).isoformat(),
        "mac_list_format": "txt",
        "mac_list_url": f"/api/production/mac-list/download?batch_id={batch_id}",
    }


def parse_iso_datetime(value, field_name):
    try:
        return datetime.fromisoformat(value)
    except (TypeError, ValueError):
        raise ValueError(f"{field_name} must be a valid ISO 8601 datetime")


def validate_result_records(
    records,
    field_name,
    require_result=False,
    require_reason=False,
    require_session_id=False,
    allowed_session_ids=None,
):
    if not isinstance(records, list):
        raise ValueError(f"{field_name} must be a list")

    for index, record in enumerate(records, start=1):
        if not isinstance(record, dict):
            raise ValueError(f"{field_name}[{index}] must be an object")
        if require_session_id and not record.get("session_id"):
            raise ValueError(f"{field_name}[{index}].session_id is required")
        if require_session_id and allowed_session_ids is not None:
            session_id = record.get("session_id")
            if session_id not in allowed_session_ids:
                raise ValueError(
                    f"{field_name}[{index}].session_id must exist in included_sessions"
                )
        if not record.get("mac"):
            raise ValueError(f"{field_name}[{index}].mac is required")
        if require_result and not record.get("result"):
            raise ValueError(f"{field_name}[{index}].result is required")
        if not record.get("time"):
            raise ValueError(f"{field_name}[{index}].time is required")
        parse_iso_datetime(record.get("time"), f"{field_name}[{index}].time")
        if require_reason and not record.get("reason"):
            raise ValueError(f"{field_name}[{index}].reason is required")


def validate_invalid_records(
    invalid_list,
    require_session_id=False,
    allowed_session_ids=None,
):
    if not isinstance(invalid_list, list):
        raise ValueError("invalid must be a list")
    for index, invalid_record in enumerate(invalid_list, start=1):
        if not isinstance(invalid_record, dict):
            raise ValueError(f"invalid[{index}] must be an object")
        if require_session_id and not invalid_record.get("session_id"):
            raise ValueError(f"invalid[{index}].session_id is required")
        if require_session_id and allowed_session_ids is not None:
            session_id = invalid_record.get("session_id")
            if session_id not in allowed_session_ids:
                raise ValueError(
                    f"invalid[{index}].session_id must exist in included_sessions"
                )
        if not invalid_record.get("mac") or not isinstance(invalid_record.get("mac"), str):
            raise ValueError(f"invalid[{index}].mac is required")
        if not invalid_record.get("time"):
            raise ValueError(f"invalid[{index}].time is required")
        parse_iso_datetime(invalid_record.get("time"), f"invalid[{index}].time")


def validate_production_result_payload(payload):
    required_fields = [
        "batch_id",
        "factory_id",
        "app_version",
        "test_start_time",
        "test_end_time",
        "statistics",
        "success_records",
        "fail_records",
        "invalid",
    ]
    for field_name in required_fields:
        if field_name not in payload:
            raise ValueError(f"{field_name} is required")

    if not payload["batch_id"]:
        raise ValueError("batch_id is required")
    if not payload["factory_id"]:
        raise ValueError("factory_id is required")
    if not payload["app_version"]:
        raise ValueError("app_version is required")
    if not payload.get("report_id"):
        raise ValueError("report_id is required")
    if not payload.get("report_digest"):
        raise ValueError("report_digest is required")

    start_time = parse_iso_datetime(payload["test_start_time"], "test_start_time")
    end_time = parse_iso_datetime(payload["test_end_time"], "test_end_time")
    if end_time < start_time:
        raise ValueError("test_end_time must be greater than or equal to test_start_time")

    statistics = payload["statistics"]
    if not isinstance(statistics, dict):
        raise ValueError("statistics must be an object")

    required_statistics_fields = [
        "expected_count",
        "actual_count",
        "success_count",
        "fail_count",
        "success_rate",
    ]
    for field_name in required_statistics_fields:
        if field_name not in statistics:
            raise ValueError(f"statistics.{field_name} is required")

    validate_result_records(payload["success_records"], "success_records")
    validate_result_records(payload["fail_records"], "fail_records", require_result=True, require_reason=True)
    validate_invalid_records(payload["invalid"])


def validate_included_sessions(sessions):
    if sessions is None:
        return
    if not isinstance(sessions, list):
        raise ValueError("included_sessions must be a list")
    for index, session in enumerate(sessions, start=1):
        if not isinstance(session, dict):
            raise ValueError(f"included_sessions[{index}] must be an object")
        if not session.get("session_id"):
            raise ValueError(f"included_sessions[{index}].session_id is required")
        if not session.get("test_start_time"):
            raise ValueError(f"included_sessions[{index}].test_start_time is required")
        if not session.get("test_end_time"):
            raise ValueError(f"included_sessions[{index}].test_end_time is required")
        start_time = parse_iso_datetime(
            session.get("test_start_time"),
            f"included_sessions[{index}].test_start_time",
        )
        end_time = parse_iso_datetime(
            session.get("test_end_time"),
            f"included_sessions[{index}].test_end_time",
        )
        if end_time < start_time:
            raise ValueError(
                f"included_sessions[{index}].test_end_time must be greater than or equal to included_sessions[{index}].test_start_time"
            )


def validate_batch_result_payload(payload):
    if not isinstance(payload, dict):
        raise ValueError("file root must be a JSON object")

    required_fields = [
        "batch_id",
        "factory_id",
        "app_version",
        "aggregate_start_time",
        "aggregate_end_time",
        "statistics",
        "success_records",
        "fail_records",
        "invalid",
    ]
    for field_name in required_fields:
        if field_name not in payload:
            raise ValueError(f"{field_name} is required")

    if not payload["batch_id"]:
        raise ValueError("batch_id is required")
    if not payload["factory_id"]:
        raise ValueError("factory_id is required")
    if not payload["app_version"]:
        raise ValueError("app_version is required")

    start_time = parse_iso_datetime(payload["aggregate_start_time"], "aggregate_start_time")
    end_time = parse_iso_datetime(payload["aggregate_end_time"], "aggregate_end_time")
    if end_time < start_time:
        raise ValueError("aggregate_end_time must be greater than or equal to aggregate_start_time")

    statistics = payload["statistics"]
    if not isinstance(statistics, dict):
        raise ValueError("statistics must be an object")

    required_statistics_fields = [
        "expected_count",
        "actual_count",
        "success_count",
        "fail_count",
        "invalid_count",
        "success_rate",
    ]
    for field_name in required_statistics_fields:
        if field_name not in statistics:
            raise ValueError(f"statistics.{field_name} is required")

    included_sessions = payload.get("included_sessions")
    validate_included_sessions(included_sessions)
    included_session_ids = {session["session_id"] for session in (included_sessions or [])}
    validate_result_records(
        payload["success_records"],
        "success_records",
        require_session_id=True,
        allowed_session_ids=included_session_ids,
    )
    validate_result_records(
        payload["fail_records"],
        "fail_records",
        require_result=True,
        require_reason=True,
        require_session_id=True,
        allowed_session_ids=included_session_ids,
    )
    validate_invalid_records(
        payload["invalid"],
        require_session_id=True,
        allowed_session_ids=included_session_ids,
    )


def read_uploaded_json_file(uploaded_file):
    file_bytes = uploaded_file.read()
    if not file_bytes:
        raise ValueError("file is empty")
    try:
        payload = json.loads(file_bytes.decode("utf-8"))
    except UnicodeDecodeError as exc:
        raise ValueError("file must be valid UTF-8 JSON") from exc
    except json.JSONDecodeError as exc:
        raise ValueError("file must contain valid JSON") from exc
    return file_bytes, payload


def build_sha256_hex(file_bytes):
    return hashlib.sha256(file_bytes).hexdigest()


def validate_batch_result_upload_request(form_data, payload, file_digest):
    required_form_fields = [
        "batch_id",
        "factory_id",
        "batch_report_id",
        "batch_report_digest",
        "app_version",
        "aggregate_start_time",
        "aggregate_end_time",
    ]
    for field_name in required_form_fields:
        if not (form_data.get(field_name) or "").strip():
            raise ValueError(f"{field_name} is required")

    if form_data["batch_report_digest"].strip().lower() != file_digest.lower():
        raise ValueError("batch_report_digest does not match uploaded file content")

    mirrored_fields = [
        "batch_id",
        "factory_id",
        "app_version",
        "aggregate_start_time",
        "aggregate_end_time",
    ]
    for field_name in mirrored_fields:
        payload_value = payload.get(field_name)
        form_value = form_data[field_name].strip()
        if payload_value != form_value:
            raise ValueError(f"{field_name} in file does not match form field")


def build_result_file_path(batch_id, report_id):
    safe_batch_id = secure_filename(batch_id)
    safe_report_id = secure_filename(report_id)
    file_name = f"{safe_report_id or safe_batch_id}.json"
    result_folder = ensure_batch_result_folder(batch_id)
    return os.path.join(result_folder, file_name), file_name


def build_batch_result_file_path(batch_id, factory_id, batch_report_id):
    safe_batch_id = secure_filename(batch_id)
    safe_factory_id = secure_filename(factory_id)
    safe_report_id = secure_filename(batch_report_id)
    file_name = f"batch_{safe_batch_id}_{safe_factory_id}_{safe_report_id or safe_batch_id}.json"
    result_folder = ensure_batch_result_folder(batch_id)
    return os.path.join(result_folder, file_name), file_name


def get_upload_index_path(batch_id):
    result_folder = ensure_batch_result_folder(batch_id)
    if not result_folder:
        return None
    return os.path.join(result_folder, "_upload_index.json")


def load_upload_index(batch_id):
    index_path = get_upload_index_path(batch_id)
    if not index_path or not os.path.exists(index_path):
        return {}
    with open(index_path, "r", encoding="utf-8") as index_file:
        return json.load(index_file)


def save_upload_index(batch_id, index_payload):
    index_path = get_upload_index_path(batch_id)
    if not index_path:
        return
    with open(index_path, "w", encoding="utf-8") as index_file:
        json.dump(index_payload, index_file, ensure_ascii=False, indent=2)


def get_batch_upload_index_path(batch_id):
    result_folder = ensure_batch_result_folder(batch_id)
    if not result_folder:
        return None
    return os.path.join(result_folder, "_batch_upload_index.json")


def load_batch_upload_index(batch_id):
    index_path = get_batch_upload_index_path(batch_id)
    if not index_path or not os.path.exists(index_path):
        return {}
    with open(index_path, "r", encoding="utf-8") as index_file:
        return json.load(index_file)


def save_batch_upload_index(batch_id, index_payload):
    index_path = get_batch_upload_index_path(batch_id)
    if not index_path:
        return
    with open(index_path, "w", encoding="utf-8") as index_file:
        json.dump(index_payload, index_file, ensure_ascii=False, indent=2)


def build_upload_dedup_key(batch_id, factory_id, report_digest):
    return f"{batch_id}|{factory_id}|{report_digest}"


def generate_upload_metadata():
    now = datetime.now().astimezone()
    upload_id = f"UPLOAD{now.strftime('%Y%m%d%H%M%S')}"
    upload_time = now.isoformat()
    return upload_id, upload_time


def list_batch_result_files(batch_id):
    resources, error_message = load_batch_resources(batch_id)
    if error_message:
        return None, error_message

    result_folder = get_batch_result_folder(batch_id)
    if not result_folder or not os.path.isdir(result_folder):
        return None, f"Production result folder not found for batch_id: {batch_id}"

    prefix = f"{resources['batch_id']}_"
    result_files = [
        os.path.join(result_folder, file_name)
        for file_name in os.listdir(result_folder)
        if file_name.startswith(prefix) and file_name.endswith(".json")
    ]
    return result_files, None


def load_latest_batch_result(batch_id):
    result_files, error_message = list_batch_result_files(batch_id)
    if error_message:
        return None, error_message
    if not result_files:
        return None, f"Production result not found for batch_id: {batch_id}"

    latest_file = max(result_files, key=os.path.getmtime)
    with open(latest_file, "r", encoding="utf-8") as result_file:
        return json.load(result_file), None


def list_batch_cumulative_result_files(batch_id, factory_id=None):
    resources, error_message = load_batch_resources(batch_id)
    if error_message:
        return None, error_message

    result_folder = get_batch_result_folder(batch_id)
    if not result_folder or not os.path.isdir(result_folder):
        return None, f"Batch cumulative result folder not found for batch_id: {batch_id}"

    safe_factory_id = secure_filename(factory_id) if factory_id else None
    prefix = f"batch_{resources['batch_id']}_"
    if safe_factory_id:
        prefix = f"{prefix}{safe_factory_id}_"
    result_files = [
        os.path.join(result_folder, file_name)
        for file_name in os.listdir(result_folder)
        if file_name.startswith(prefix) and file_name.endswith(".json")
    ]
    return result_files, None


def load_latest_batch_cumulative_result(batch_id, factory_id=None):
    result_files, error_message = list_batch_cumulative_result_files(batch_id, factory_id)
    if error_message:
        return None, error_message
    if not result_files:
        message = f"Batch cumulative result not found for batch_id: {batch_id}"
        if factory_id:
            message += f" factory_id: {factory_id}"
        return None, message

    latest_file = max(result_files, key=os.path.getmtime)
    with open(latest_file, "r", encoding="utf-8") as result_file:
        return json.load(result_file), None


@jwt.expired_token_loader
def expired_token_callback(jwt_header, jwt_payload):
    return jsonify({"msg": "Token has expired"}), 401


@jwt.invalid_token_loader
def invalid_token_callback(reason):
    return jsonify({"msg": f"Invalid token: {reason}"}), 401


@jwt.unauthorized_loader
def missing_token_callback(reason):
    return jsonify({"msg": f"Missing token: {reason}"}), 401


@jwt.revoked_token_loader
def revoked_token_callback(jwt_header, jwt_payload):
    return jsonify({"msg": "Token has been revoked"}), 401


@jwt.token_in_blocklist_loader
def is_token_revoked(jwt_header, jwt_payload):
    return jwt_payload["jti"] in token_blocklist


@app.route("/api/login", methods=["POST"])
def login():
    data = request.get_json(silent=True) or {}
    username = data.get("username")
    password = data.get("password")

    if username not in users or users[username] != password:
        return jsonify({"msg": "Invalid username or password"}), 401

    revoke_user_active_tokens(username)

    access_token = create_access_token(identity=username)
    refresh_token = create_refresh_token(identity=username)
    remember_user_tokens(username, access_token, refresh_token)

    return jsonify(
        access_token=access_token,
        access_expires=get_expire_seconds("JWT_ACCESS_TOKEN_EXPIRES"),
        refresh_token=refresh_token,
        refresh_expires=get_expire_seconds("JWT_REFRESH_TOKEN_EXPIRES"),
    )


@app.route("/api/refresh", methods=["POST"])
@jwt_required(refresh=True)
def refresh():
    log_current_token("refresh")
    current_user = get_jwt_identity()
    current_jwt = get_jwt()

    revoke_token_by_jti(current_jwt["jti"])
    revoke_token_by_jti(user_active_tokens.get(current_user, {}).get("access_jti"))

    new_access_token = create_access_token(identity=current_user)
    new_refresh_token = create_refresh_token(identity=current_user)
    remember_user_tokens(current_user, new_access_token, new_refresh_token)

    return jsonify(
        access_token=new_access_token,
        access_expires=get_expire_seconds("JWT_ACCESS_TOKEN_EXPIRES"),
        refresh_token=new_refresh_token,
        refresh_expires=get_expire_seconds("JWT_REFRESH_TOKEN_EXPIRES"),
    )


@app.route("/api/logout", methods=["POST"])
@jwt_required()
def logout():
    log_current_token("logout")
    current_user = get_jwt_identity()
    current_jwt = get_jwt()
    active_tokens = user_active_tokens.get(current_user, {})

    revoke_token_by_jti(current_jwt["jti"])
    if active_tokens.get("access_jti") == current_jwt["jti"]:
        revoke_token_by_jti(active_tokens.get("refresh_jti"))
        user_active_tokens.pop(current_user, None)

    return jsonify({"msg": "Logout successful"})


@app.route("/api/upload", methods=["POST"])
@jwt_required()
def upload_file():
    log_current_token("upload")

    if "file" not in request.files:
        return jsonify({"msg": "No file uploaded"}), 400

    file = request.files["file"]
    if file.filename == "":
        return jsonify({"msg": "Empty filename"}), 400

    filename = secure_filename(file.filename)
    batch_id = resolve_upload_batch_id()
    if batch_id:
        resources, error_message = load_batch_resources(batch_id)
        if error_message:
            status_code = get_batch_error_status(error_message)
            return jsonify({"msg": error_message}), status_code

        target_folder = ensure_batch_result_folder(batch_id)
        file.save(os.path.join(target_folder, filename))
        file_url = f"/api/download/batch/{resources['batch_id']}/{filename}"
    else:
        file.save(os.path.join(app.config["UPLOAD_FOLDER"], filename))
        file_url = f"/api/download/{filename}"

    return jsonify(
        {
            "filename": filename,
            "url": file_url,
        }
    )


@app.route("/api/download/<filename>", methods=["GET"])
@jwt_required()
def download_file(filename):
    log_current_token("download")
    return send_from_directory(app.config["UPLOAD_FOLDER"], filename, as_attachment=True)


@app.route("/api/download/batch/<batch_id>/<filename>", methods=["GET"])
@jwt_required()
def download_batch_file(batch_id, filename):
    log_current_token("download_batch_file")
    resources, error_message = load_batch_resources(batch_id)
    if error_message:
        status_code = get_batch_error_status(error_message)
        return jsonify({"msg": error_message}), status_code

    result_folder = get_batch_result_folder(resources["batch_id"])
    return send_from_directory(result_folder, filename, as_attachment=True)


@app.route("/api/storage/files", methods=["GET"])
@jwt_required()
def list_files():
    log_current_token("storage_files")
    batch_id = (request.args.get("batch_id") or "").strip()
    if batch_id:
        batch_dir = get_batch_directory_path(batch_id)
        if not batch_dir:
            return jsonify({"msg": "Invalid batch_id"}), 400
        if not os.path.isdir(batch_dir):
            return jsonify({"msg": f"Batch directory not found: batch/{batch_id}"}), 404
        return jsonify(list_relative_files(batch_dir))

    files = sorted(
        file_name
        for file_name in os.listdir(app.config["UPLOAD_FOLDER"])
        if os.path.isfile(os.path.join(app.config["UPLOAD_FOLDER"], file_name))
    )
    return jsonify(files)


@app.route("/api/production/summary", methods=["GET"])
@jwt_required()
def get_production_summary():
    log_current_token("production_summary")
    batch_id = (request.args.get("batch_id") or "").strip()
    if not batch_id:
        return jsonify({"code": 400, "message": "batch_id is required", "data": None}), 400

    resources, error_message = load_batch_resources(batch_id)
    if error_message:
        status_code = get_batch_error_status(error_message)
        return jsonify({"code": status_code, "message": error_message, "data": None}), status_code

    data = build_production_summary_data(resources)
    return jsonify({"code": 200, "message": "success", "data": data})


@app.route("/api/production/mac-list/download", methods=["GET"])
@jwt_required()
def download_production_mac_list():
    log_current_token("production_mac_list_download")
    batch_id = (request.args.get("batch_id") or "").strip()
    if not batch_id:
        return jsonify({"code": 400, "message": "batch_id is required", "data": None}), 400

    resources, error_message = load_batch_resources(batch_id)
    if error_message:
        status_code = get_batch_error_status(error_message)
        return jsonify({"code": status_code, "message": error_message, "data": None}), status_code

    mac_list = resources["mac_list"]
    response = Response("\n".join(mac_list), mimetype="text/plain")
    response.headers["Content-Disposition"] = f'attachment; filename="{batch_id}_mac_list.txt"'
    response.headers["ETag"] = build_mac_list_hash(mac_list)
    response.headers["X-Mac-List-Version"] = datetime.fromtimestamp(
        os.path.getmtime(resources["mac_file_path"]),
        tz=timezone.utc,
    ).isoformat()
    response.headers["X-Mac-List-Count"] = str(len(mac_list))
    return response


@app.route("/api/production/mac-list", methods=["GET"])
@jwt_required()
def get_production_mac_list():
    log_current_token("production_mac_list")
    batch_id = (request.args.get("batch_id") or "").strip()
    if not batch_id:
        return jsonify({"code": 400, "message": "batch_id is required", "data": None}), 400

    resources, error_message = load_batch_resources(batch_id)
    if error_message:
        status_code = get_batch_error_status(error_message)
        return jsonify({"code": status_code, "message": error_message, "data": None}), status_code

    data = {
        **build_production_summary_data(resources),
        "mac_list": resources["mac_list"],
    }
    return jsonify({"code": 200, "message": "success", "data": data})


@app.route("/api/production/result/upload", methods=["POST"])
@jwt_required()
def upload_production_result():
    log_current_token("production_result_upload")
    payload = request.get_json(silent=True) or {}

    try:
        validate_production_result_payload(payload)
    except ValueError as exc:
        return jsonify({"code": 400, "message": str(exc), "data": None}), 400

    batch_id = payload["batch_id"].strip()
    resources, error_message = load_batch_resources(batch_id)
    if error_message:
        status_code = get_batch_error_status(error_message)
        return jsonify({"code": status_code, "message": error_message, "data": None}), status_code

    dedup_key = build_upload_dedup_key(batch_id, payload["factory_id"], payload["report_digest"])
    upload_index = load_upload_index(batch_id)
    existing_upload = upload_index.get(dedup_key)
    if existing_upload:
        existing_file = os.path.join(get_batch_result_folder(batch_id), existing_upload["file_name"])
        if os.path.exists(existing_file):
            return jsonify(
                {
                    "code": 200,
                    "message": "upload success",
                    "data": {
                        "upload_id": existing_upload["upload_id"],
                        "upload_time": existing_upload["upload_time"],
                        "report_id": existing_upload.get("report_id"),
                        "report_digest": existing_upload.get("report_digest"),
                        "duplicate": True,
                    },
                }
            )

    result_file_path, result_file_name = build_result_file_path(batch_id, payload["report_id"])
    upload_id, upload_time = generate_upload_metadata()
    result_payload = {
        **payload,
        "upload_id": upload_id,
        "uploaded_by": get_jwt_identity(),
        "uploaded_at": upload_time,
        "batch_mac_count": len(resources["mac_list"]),
    }

    with open(result_file_path, "w", encoding="utf-8") as result_file:
        json.dump(result_payload, result_file, ensure_ascii=False, indent=2)

    upload_index[dedup_key] = {
        "upload_id": upload_id,
        "upload_time": upload_time,
        "report_id": payload["report_id"],
        "report_digest": payload["report_digest"],
        "file_name": result_file_name,
    }
    save_upload_index(batch_id, upload_index)

    return jsonify(
        {
            "code": 200,
            "message": "upload success",
            "data": {
                "upload_id": upload_id,
                "upload_time": upload_time,
                "report_id": payload["report_id"],
                "report_digest": payload["report_digest"],
                "duplicate": False,
            },
        }
    )


@app.route("/api/production/statistics", methods=["GET"])
@jwt_required()
def get_production_statistics():
    log_current_token("production_statistics")
    batch_id = (request.args.get("batch_id") or "").strip()
    if not batch_id:
        return jsonify({"code": 400, "message": "batch_id is required", "data": None}), 400

    result_payload, error_message = load_latest_batch_result(batch_id)
    if error_message:
        status_code = 404 if error_message.startswith("Production result not found") else 400
        return jsonify({"code": status_code, "message": error_message, "data": None}), status_code

    statistics = result_payload.get("statistics", {})
    data = {
        "batch_id": result_payload.get("batch_id", batch_id),
        "expected_count": statistics.get("expected_count"),
        "actual_count": statistics.get("actual_count"),
        "success_count": statistics.get("success_count"),
        "fail_count": statistics.get("fail_count"),
        "success_rate": statistics.get("success_rate"),
        "test_start_time": result_payload.get("test_start_time"),
        "test_end_time": result_payload.get("test_end_time"),
        "status": "completed",
    }
    return jsonify({"code": 200, "message": "success", "data": data})


@app.route("/api/production/batch-result/upload-file", methods=["POST"])
@jwt_required()
def upload_batch_result_file():
    log_current_token("production_batch_result_upload_file")

    uploaded_file = request.files.get("file")
    if uploaded_file is None:
        return jsonify({"code": 400, "message": "file is required", "data": None}), 400
    if not uploaded_file.filename:
        return jsonify({"code": 400, "message": "file filename is required", "data": None}), 400

    source_file_name = secure_filename(uploaded_file.filename)
    if not source_file_name:
        return jsonify({"code": 400, "message": "invalid file name", "data": None}), 400
    if not source_file_name.lower().endswith(".json"):
        return jsonify({"code": 400, "message": "file must be a .json file", "data": None}), 400

    try:
        file_bytes, payload = read_uploaded_json_file(uploaded_file)
        file_digest = build_sha256_hex(file_bytes)
        validate_batch_result_payload(payload)
        validate_batch_result_upload_request(request.form, payload, file_digest)
    except ValueError as exc:
        return jsonify({"code": 400, "message": str(exc), "data": None}), 400

    batch_id = request.form["batch_id"].strip()
    factory_id = request.form["factory_id"].strip()
    batch_report_id = request.form["batch_report_id"].strip()
    batch_report_digest = request.form["batch_report_digest"].strip().lower()

    resources, error_message = load_batch_resources(batch_id)
    if error_message:
        status_code = get_batch_error_status(error_message)
        return jsonify({"code": status_code, "message": error_message, "data": None}), status_code

    dedup_key = build_upload_dedup_key(batch_id, factory_id, batch_report_digest)
    upload_index = load_batch_upload_index(batch_id)
    existing_upload = upload_index.get(dedup_key)
    if existing_upload:
        existing_file_name = existing_upload.get("file_name")
        if existing_file_name:
            existing_file_path = os.path.join(ensure_batch_result_folder(batch_id), existing_file_name)
            if os.path.exists(existing_file_path):
                return jsonify(
                    {
                        "code": 200,
                        "message": "duplicate upload",
                        "data": {
                            "batch_id": batch_id,
                            "factory_id": factory_id,
                            "batch_report_id": existing_upload.get("batch_report_id", batch_report_id),
                            "batch_report_digest": existing_upload.get(
                                "batch_report_digest",
                                batch_report_digest,
                            ),
                            "duplicate": True,
                            "upload_id": existing_upload.get("upload_id"),
                            "uploaded_at": existing_upload.get("uploaded_at"),
                            "file_name": existing_file_name,
                        },
                    }
                )

    result_file_path, result_file_name = build_batch_result_file_path(batch_id, factory_id, batch_report_id)
    upload_id, upload_time = generate_upload_metadata()

    with open(result_file_path, "wb") as result_file:
        result_file.write(file_bytes)

    upload_index[dedup_key] = {
        "upload_id": upload_id,
        "uploaded_at": upload_time,
        "batch_id": batch_id,
        "factory_id": factory_id,
        "batch_report_id": batch_report_id,
        "batch_report_digest": batch_report_digest,
        "file_name": result_file_name,
        "source_file_name": source_file_name,
        "aggregate_start_time": payload.get("aggregate_start_time"),
        "aggregate_end_time": payload.get("aggregate_end_time"),
        "app_version": payload.get("app_version"),
        "batch_mac_count": len(resources["mac_list"]),
    }
    save_batch_upload_index(batch_id, upload_index)

    return jsonify(
        {
            "code": 200,
            "message": "upload success",
            "data": {
                "batch_id": batch_id,
                "factory_id": factory_id,
                "batch_report_id": batch_report_id,
                "batch_report_digest": batch_report_digest,
                "duplicate": False,
                "upload_id": upload_id,
                "uploaded_at": upload_time,
                "file_name": result_file_name,
            },
        }
    )


@app.route("/api/production/batch-statistics", methods=["GET"])
@jwt_required()
def get_batch_statistics():
    log_current_token("production_batch_statistics")

    batch_id = (request.args.get("batch_id") or "").strip()
    factory_id = (request.args.get("factory_id") or "").strip()

    if not batch_id:
        return jsonify({"code": 400, "message": "batch_id is required", "data": None}), 400

    result_payload, error_message = load_latest_batch_cumulative_result(batch_id, factory_id or None)
    if error_message:
        status_code = 404 if "not found" in error_message.lower() else 400
        return jsonify({"code": status_code, "message": error_message, "data": None}), status_code

    statistics = result_payload.get("statistics") or {}
    data = {
        "batch_id": result_payload.get("batch_id", batch_id),
        "factory_id": result_payload.get("factory_id", factory_id or None),
        "aggregate_start_time": result_payload.get("aggregate_start_time"),
        "aggregate_end_time": result_payload.get("aggregate_end_time"),
        "expected_count": statistics.get("expected_count", 0),
        "actual_count": statistics.get("actual_count", 0),
        "success_count": statistics.get("success_count", 0),
        "fail_count": statistics.get("fail_count", 0),
        "invalid_count": statistics.get("invalid_count", 0),
        "success_rate": statistics.get("success_rate", 0),
        "status": "completed",
    }
    return jsonify({"code": 200, "message": "success", "data": data})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
