#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${REPO_ROOT}"

if ! command -v rg >/dev/null 2>&1; then
  echo "Demo boundary check: FAIL"
  echo "- ripgrep (rg) is required but not installed."
  exit 1
fi

errors=()

check_pattern() {
  local label="$1"
  local pattern="$2"
  local output
  output="$(rg -n -S --glob '*.java' "${pattern}" src/main/java 2>/dev/null || true)"
  if [[ -n "${output}" ]]; then
    errors+=("${label} -> '${pattern}'")
    while IFS= read -r line; do
      [[ -n "${line}" ]] || continue
      errors+=("  ${line}")
    done <<< "${output}"
  fi
}

# PRO-only endpoint and capability markers forbidden in the public demo repository.
check_pattern "Forbidden pro endpoint marker" "/admin/security/api-keys"
check_pattern "Forbidden pro endpoint marker" "/integrations/webhooks"
check_pattern "Forbidden pro endpoint marker" "/admin/exports"
check_pattern "Forbidden pro endpoint marker" "/publish/dry-run"
check_pattern "Forbidden pro auth marker" "X-API-Key"
check_pattern "Forbidden pro model/service marker" "\\bFieldPermission\\b"
check_pattern "Forbidden pro model/service marker" "\\breadRoles\\b"
check_pattern "Forbidden pro model/service marker" "\\bwriteRoles\\b"
check_pattern "Forbidden pro model/service marker" "\\bmasking\\b"
check_pattern "Forbidden pro model/service marker" "\\bApiKey\\b"
check_pattern "Forbidden pro model/service marker" "\\bWebhook\\b"
check_pattern "Forbidden pro model/service marker" "\\bExportJob\\b"
check_pattern "Forbidden pro persistence marker" "\\baudit_logs\\b"

if [[ "${#errors[@]}" -gt 0 ]]; then
  echo "Demo boundary check: FAIL"
  for err in "${errors[@]}"; do
    echo "- ${err}"
  done
  exit 1
fi

echo "Demo boundary check: OK"
