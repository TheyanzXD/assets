FROM 17863

# Install dependensi dasar & Java 17
RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    wget \
    unzip \
    git \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Set Environment Variable
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# Download & Extract Android Commandline Tools
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp && \
    mv /tmp/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Lisensi & Komponen SDK yang dibutuhkan
RUN yes | sdkmanager --licenses && \
    sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

WORKDIR /workspace
