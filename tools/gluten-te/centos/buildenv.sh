#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -ex

BASEDIR=$(dirname $0)

source "$BASEDIR/defaults.conf"

# Docker cache image used to speed-up builds
DOCKER_CACHE_IMAGE=${DOCKER_CACHE_IMAGE:-$DEFAULT_DOCKER_CACHE_IMAGE}

# HTTP proxy
HTTP_PROXY_HOST=${HTTP_PROXY_HOST:-$DEFAULT_HTTP_PROXY_HOST}
HTTP_PROXY_PORT=${HTTP_PROXY_PORT:-$DEFAULT_HTTP_PROXY_PORT}

# If on, use maven mirror settings for PRC's network environment
USE_ALI_MAVEN_MIRROR=${USE_ALI_MAVEN_MIRROR:-$DEFAULT_USE_ALI_MAVEN_MIRROR}

# Set timezone name
TIMEZONE=${TIMEZONE:-$DEFAULT_TIMEZONE}

# Set operating system
OS_IMAGE=${OS_IMAGE:-$DEFAULT_OS_IMAGE}

# Set os version
OS_VERSION=${OS_VERSION:-$DEFAULT_OS_VERSION}

# Build will result in this image
DOCKER_TARGET_IMAGE_BUILDENV=${DOCKER_TARGET_IMAGE_BUILDENV:-$DEFAULT_DOCKER_TARGET_IMAGE_BUILDENV}

DOCKER_TARGET_IMAGE_BUILDENV_WITH_OS_IMAGE="$DOCKER_TARGET_IMAGE_BUILDENV-$OS_IMAGE"

if [ "$USE_ALI_MAVEN_MIRROR" == "ON" ]
then
  MAVEN_MIRROR_URL='https://maven.aliyun.com/repository/public'
else
  MAVEN_MIRROR_URL=
fi

##

BUILDENV_DOCKER_BUILD_ARGS=

BUILDENV_DOCKER_BUILD_ARGS="$BUILDENV_DOCKER_BUILD_ARGS --ulimit nofile=8192:8192"
BUILDENV_DOCKER_BUILD_ARGS="$BUILDENV_DOCKER_BUILD_ARGS --build-arg TIMEZONE=$TIMEZONE"
BUILDENV_DOCKER_BUILD_ARGS="$BUILDENV_DOCKER_BUILD_ARGS --build-arg OS_IMAGE=$OS_IMAGE --build-arg OS_VERSION=$OS_VERSION"
BUILDENV_DOCKER_BUILD_ARGS="$BUILDENV_DOCKER_BUILD_ARGS --build-arg MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL"
BUILDENV_DOCKER_BUILD_ARGS="$BUILDENV_DOCKER_BUILD_ARGS --build-arg HTTP_PROXY_HOST=$HTTP_PROXY_HOST"
BUILDENV_DOCKER_BUILD_ARGS="$BUILDENV_DOCKER_BUILD_ARGS --build-arg HTTP_PROXY_PORT=$HTTP_PROXY_PORT"
BUILDENV_DOCKER_BUILD_ARGS="$BUILDENV_DOCKER_BUILD_ARGS -f $BASEDIR/dockerfile-buildenv"
BUILDENV_DOCKER_BUILD_ARGS="$BUILDENV_DOCKER_BUILD_ARGS --target gluten-buildenv"
BUILDENV_DOCKER_BUILD_ARGS="$BUILDENV_DOCKER_BUILD_ARGS -t $DOCKER_TARGET_IMAGE_BUILDENV_WITH_OS_IMAGE"

if [ -n "$DOCKER_CACHE_IMAGE" ]
then
  BUILDENV_DOCKER_BUILD_ARGS="$BUILDENV_DOCKER_BUILD_ARGS --cache-from $DOCKER_CACHE_IMAGE"
fi

BUILDENV_DOCKER_BUILD_ARGS="$BUILDENV_DOCKER_BUILD_ARGS $BASEDIR"

docker build $BUILDENV_DOCKER_BUILD_ARGS

# EOF
