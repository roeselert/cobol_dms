"""Bearer-token auth between the DMS app and this service.

Fails closed, mirroring the main app's security-mode invariant: without a
configured DMS_SERVICE_TOKEN the work endpoints refuse requests (503) unless
auth is switched off explicitly with DMS_SERVICE_AUTH=off (local dev /
docker-compose convenience). /healthz stays unauthenticated.
"""

import os
import secrets

from fastapi import Header

from .errors import ApiError


def require_service_token(authorization: str | None = Header(default=None)) -> None:
    if os.environ.get("DMS_SERVICE_AUTH", "").lower() == "off":
        return
    token = os.environ.get("DMS_SERVICE_TOKEN", "")
    if not token:
        raise ApiError(503, "auth_unconfigured",
                       "DMS_SERVICE_TOKEN is not set; set it or disable auth with DMS_SERVICE_AUTH=off")
    if authorization is None or not authorization.startswith("Bearer "):
        raise ApiError(401, "unauthorized", "missing bearer token")
    if not secrets.compare_digest(authorization.removeprefix("Bearer "), token):
        raise ApiError(401, "unauthorized", "invalid bearer token")
