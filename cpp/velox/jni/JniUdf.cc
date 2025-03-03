/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "JniUdf.h"
#include "jni/JniCommon.h"
#include "udf/UdfLoader.h"
#include "utils/exception.h"

namespace {

static JavaVM* vm;

const std::string kUdfResolverClassPath = "Lorg/apache/spark/sql/expression/UDFResolver$;";

static jclass udfResolverClass;
static jmethodID registerUDFMethod;

} // namespace

void gluten::initVeloxJniUDF(JNIEnv* env) {
  if (env->GetJavaVM(&vm) != JNI_OK) {
    throw gluten::GlutenException("Unable to get JavaVM instance");
  }

  // classes
  udfResolverClass = createGlobalClassReferenceOrError(env, kUdfResolverClassPath.c_str());

  // methods
  registerUDFMethod = getMethodIdOrError(env, udfResolverClass, "registerUDF", "(Ljava/lang/String;[B)V");
}

void gluten::finalizeVeloxJniUDF(JNIEnv* env) {
  env->DeleteGlobalRef(udfResolverClass);
}

void gluten::jniLoadUdf(JNIEnv* env, const std::string& libPaths) {
  auto udfLoader = gluten::UdfLoader::getInstance();
  udfLoader->loadUdfLibraries(libPaths);

  const auto& udfMap = udfLoader->getUdfMap();
  for (const auto& udf : udfMap) {
    auto udfString = udf.second;
    jbyteArray returnType = env->NewByteArray(udf.second.length());
    env->SetByteArrayRegion(returnType, 0, udfString.length(), reinterpret_cast<const jbyte*>(udfString.c_str()));
    jstring name = env->NewStringUTF(udf.first.c_str());

    jobject instance = env->GetStaticObjectField(
        udfResolverClass, env->GetStaticFieldID(udfResolverClass, "MODULE$", kUdfResolverClassPath.c_str()));
    env->CallVoidMethod(instance, registerUDFMethod, name, returnType);
  }
}
