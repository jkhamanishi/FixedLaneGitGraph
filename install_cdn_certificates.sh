#!/bin/bash
# Download the single certificate for a repository CDN and add it to the JVM keystore

# List of endpoint URLs to check for repository host resolution
REPOSITORY_SAMPLE_URLS=(
    "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/maven-metadata.xml"
    "https://plugins.gradle.org/m2/com/gradle/gradle-enterprise-gradle-plugin/3.15.2/gradle-enterprise-gradle-plugin-3.15.2.pom"
    "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/intellij/deps/asm-all/9.6.1/asm-all-9.6.1.pom"
    "https://plugins.gradle.org/m2/com/github/ben-manes/gradle-versions-plugin/0.51.0/gradle-versions-plugin-0.51.0.pom"
)

# Check for flags
REPLACE=false
for arg in "$@"; do
    if [ "$arg" == "--replace" ]; then
        REPLACE=true
    fi
done

# Check if JAVA_HOME is set
if [ -z "$JAVA_HOME" ]; then
    echo "JAVA_HOME is not set. Please set JAVA_HOME to your JDK directory."
    exit 1
fi

# Detect platform and resolve CA_CERTS path
UNAME_OUT=$(uname | tr '[:upper:]' '[:lower:]')
if [[ "$UNAME_OUT" == *"mingw"* || "$UNAME_OUT" == *"cygwin"* || "$UNAME_OUT" == *"msys"* || "$UNAME_OUT" == *"windows"* ]]; then
    # Windows
    JAVA_HOME_WIN=$(echo "$JAVA_HOME" | sed 's:[/\\]*$::' | sed 's:/:\\:g')
    CA_CERTS="$JAVA_HOME_WIN\lib\security\cacerts"
else
    # Linux/macOS
    JAVA_HOME_UNIX=$(echo "$JAVA_HOME" | sed 's:[/\\]*$::')
    CA_CERTS="$JAVA_HOME_UNIX/lib/security/cacerts"
fi

# Verify that the keystore file exists
echo "Using keystore: $CA_CERTS"
if [ ! -f "$CA_CERTS" ]; then
    echo "Keystore not found at $CA_CERTS. Please check JAVA_HOME."
    exit 1
fi

# Function to download and install the CDN certificate
install_cdn_cert() {
    local ORIGINAL_URL="$1"

    # Extract alias from original URL (before resolution)
    if [[ "$ORIGINAL_URL" =~ ^https?://([^/]+)/?.* ]]; then
        ALIAS="cdn_${BASH_REMATCH[1]}"
    else
        echo "Could not extract host from URL: $ORIGINAL_URL"
        exit 1
    fi
    CERT_FILE="${ALIAS}.crt"

    echo "Resolving final host for: $ORIGINAL_URL"
    FINAL_URL=$(curl -Ls -o /dev/null -w '%{url_effective}' "$ORIGINAL_URL")
    if [ -z "$FINAL_URL" ]; then
        echo "Failed to resolve final URL for."
        exit 1
    fi
    echo "Final URL: $FINAL_URL"
    if [[ "$FINAL_URL" =~ ^https?://([^/]+)/?.* ]]; then
        REPO_HOST="${BASH_REMATCH[1]}"
    else
        echo "Could not extract host from final URL: $FINAL_URL"
        exit 1
    fi

    # Check if certificate is already present
    # You can check all certificates that this script installed with
    #     keytool -list -keystore "$JAVA_HOME\lib\security\cacerts" -storepass changeit | grep '^cdn_'
    keytool -list -keystore "$CA_CERTS" -storepass changeit -alias "$ALIAS" > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        if $REPLACE; then
            echo "Certificate $ALIAS already present in JVM keystore. Deleting due to --replace flag."
            keytool -delete -alias "$ALIAS" -keystore "$CA_CERTS" -storepass changeit > /dev/null 2>&1
        else
            echo "Certificate $ALIAS already present in JVM keystore. Skipping download and import."
            return 0
        fi
    fi

    echo "Downloading single certificate for $REPO_HOST..."
    echo | openssl s_client -connect "$REPO_HOST:443" -servername "$REPO_HOST" 2>/dev/null | openssl x509 -outform PEM > "$CERT_FILE"

    # Import the certificate
    echo "Adding certificate $ALIAS to JVM keystore..."
    keytool -importcert -trustcacerts -file "$CERT_FILE" -alias "$ALIAS" -keystore "$CA_CERTS" -storepass changeit -noprompt

    # Cleanup: delete the cert file
    rm -f "$CERT_FILE"
}

for REPOSITORY_SAMPLE_URL in "${REPOSITORY_SAMPLE_URLS[@]}"; do
    install_cdn_cert "$REPOSITORY_SAMPLE_URL"
done
