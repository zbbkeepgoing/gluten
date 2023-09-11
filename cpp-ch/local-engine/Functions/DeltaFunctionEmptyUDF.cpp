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
#include <Functions/IFunction.h>
#include <Functions/FunctionFactory.h>
#include <DataTypes/DataTypesNumber.h>


namespace DB
{
namespace
{

/** UDF() is a function that takes any arguments, and always returns 1.
  */
class FunctionDeltaEmptyUDF : public IFunction
{
public:
    static constexpr auto name = "deltaEmptyUDF";
    static FunctionPtr create(ContextPtr)
    {
        return std::make_shared<FunctionDeltaEmptyUDF>();
    }

    bool isVariadic() const override
    {
        return true;
    }
    size_t getNumberOfArguments() const override
    {
        return 0;
    }

    bool useDefaultImplementationForNulls() const override { return false; }
    bool isSuitableForConstantFolding() const override { return false; }
    bool isSuitableForShortCircuitArgumentsExecution(const DataTypesWithConstInfo & /*arguments*/) const override { return false; }

    bool useDefaultImplementationForLowCardinalityColumns() const override { return false; }

    bool useDefaultImplementationForSparseColumns() const override { return false; }

    String getName() const override
    {
        return name;
    }

    DataTypePtr getReturnTypeImpl(const DataTypes & /*arguments*/) const override
    {
        return std::make_shared<DataTypeUInt8>();
    }

    ColumnPtr executeImpl(const ColumnsWithTypeAndName &, const DataTypePtr &, size_t input_rows_count) const override
    {
        return DataTypeUInt8().createColumnConst(input_rows_count, 1u);
    }
};

}

REGISTER_FUNCTION(DeltaEmptyUDF)
{
    factory.registerFunction<FunctionDeltaEmptyUDF>();
}

}
