#!/bin/bash
# Creates (or updates) the receipts-secret in the receipts namespace.
# Run this once before the first deploy and again whenever any value changes.

set -e

NAMESPACE=receipts
SECRET_NAME=receipts-secret

echo "Receipts Secret Creator"
echo "========================"
echo ""
echo "Each value will be prompted."
echo ""

read -rp "Google OAuth2 Client ID: " GOOGLE_CLIENT_ID
read -rp "Google OAuth2 Client Secret: " GOOGLE_CLIENT_SECRET
read -rp "Anthropic API Key: " ANTHROPIC_API_KEY
read -rp "Google Sheet ID (receipts spreadsheet): " SHEET_ID
read -rp "Google Sheet ID (checks spreadsheet): " CHECK_SHEET_ID
read -rp "Google Drive Folder ID: " DRIVE_FOLDER_ID

echo ""
read -rp "Sheet tab name for receipts [Receipts]: " SHEET_TAB
SHEET_TAB=${SHEET_TAB:-Receipts}

read -rp "Sheet tab name for checks [Checks]: " CHECK_SHEET_TAB
CHECK_SHEET_TAB=${CHECK_SHEET_TAB:-Checks}

read -rp "Anthropic model [claude-sonnet-4-5]: " ANTHROPIC_MODEL
ANTHROPIC_MODEL=${ANTHROPIC_MODEL:-claude-sonnet-4-5}

# Validate required fields
for var in GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET ANTHROPIC_API_KEY SHEET_ID CHECK_SHEET_ID DRIVE_FOLDER_ID; do
    if [ -z "${!var}" ]; then
        echo "Error: $var is required"
        exit 1
    fi
done

echo ""
echo "Creating namespace '$NAMESPACE' if it does not exist…"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

echo "Applying secret '$SECRET_NAME' in namespace '$NAMESPACE'…"

kubectl create secret generic "$SECRET_NAME" \
    --namespace="$NAMESPACE" \
    --from-literal=GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" \
    --from-literal=GOOGLE_CLIENT_SECRET="$GOOGLE_CLIENT_SECRET" \
    --from-literal=ANTHROPIC_API_KEY="$ANTHROPIC_API_KEY" \
    --from-literal=SHEET_ID="$SHEET_ID" \
    --from-literal=SHEET_TAB="$SHEET_TAB" \
    --from-literal=CHECK_SHEET_ID="$CHECK_SHEET_ID" \
    --from-literal=CHECK_SHEET_TAB="$CHECK_SHEET_TAB" \
    --from-literal=DRIVE_FOLDER_ID="$DRIVE_FOLDER_ID" \
    --from-literal=ANTHROPIC_MODEL="$ANTHROPIC_MODEL" \
    --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "✓ Secret '$SECRET_NAME' applied in namespace '$NAMESPACE'"
echo ""
echo "To verify:"
echo "  kubectl get secret $SECRET_NAME -n $NAMESPACE"
echo "  kubectl describe secret $SECRET_NAME -n $NAMESPACE"
