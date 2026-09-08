import { numberTypes, rangeTypes } from "models";

export const validationStatusOptions = {
  VALUE_RANGE_MISSING_START_VALUE: "VALUE_RANGE_MISSING_START_VALUE",
  VALUE_RANGE_MISSING_END_VALUE: "VALUE_RANGE_MISSING_END_VALUE",
  VALUE_RANGE_ORDER_ERROR: "VALUE_RANGE_ORDER_ERROR",
  VALUE_RANGE_IS_VALID: "VALUE_RANGE_IS_VALID",
  VALUE_IS_VALID: "VALUE_IS_VALID",
  FLAG_IS_VALID: "FLAG_IS_VALID",
  VALUE_MISSING_ERROR: "VALUE_MISSING_ERROR",
  OPERATOR_ERROR: "OPERATOR_ERROR",
  UNIT_ERROR: "UNIT_ERROR",
  FLAG_ERROR: "FLAG_ERROR",
};

export function validateLabValues(constraintType, startValue, endValue, unit) {
  const defaultStatusText = "specify values";
  const hasOperator = [...numberTypes, ...rangeTypes].includes(constraintType);
  const isRangeOperator = rangeTypes.includes(constraintType);
  const hasStartVal = startValue === 0 || Boolean(startValue);
  const hasEndVal = endValue === 0 || Boolean(endValue);
  const orderIsLogical =
    hasStartVal &&
    hasEndVal &&
    Number.parseInt(startValue, 10) < Number.parseInt(endValue, 10);
  const hasUnit = unit !== "";

  const getValidationStatus = () => {
    if (!hasOperator) {
      return null;
    }
    if (isRangeOperator && orderIsLogical && hasUnit) {
      return validationStatusOptions.VALUE_RANGE_IS_VALID;
    }
    if (isRangeOperator && !hasStartVal) {
      return validationStatusOptions.VALUE_RANGE_MISSING_START_VALUE;
    }
    if (isRangeOperator && !hasEndVal) {
      return validationStatusOptions.VALUE_RANGE_MISSING_END_VALUE;
    }
    if (isRangeOperator && !orderIsLogical) {
      return validationStatusOptions.VALUE_RANGE_ORDER_ERROR;
    }
    if (hasOperator && hasStartVal && hasUnit) {
      return validationStatusOptions.VALUE_IS_VALID;
    }
    if (!hasStartVal) {
      return validationStatusOptions.VALUE_MISSING_ERROR;
    }
    if (!hasUnit) {
      return validationStatusOptions.UNIT_ERROR;
    }
    return null;
  };

  const getValidationText = (status) => {
    switch (status) {
      case validationStatusOptions.VALUE_RANGE_IS_VALID:
        return `${constraintType} ${startValue} - ${endValue} ${unit}`;
      case validationStatusOptions.VALUE_MISSING_ERROR:
      case validationStatusOptions.VALUE_RANGE_MISSING_START_VALUE:
      case validationStatusOptions.VALUE_RANGE_MISSING_END_VALUE:
        return "required";
      case validationStatusOptions.VALUE_RANGE_ORDER_ERROR:
        return "value order error";
      case validationStatusOptions.UNIT_ERROR:
        return "Select Unit*";

      case validationStatusOptions.VALUE_IS_VALID:
        return `${constraintType} ${startValue} ${unit}`;
      default:
        return defaultStatusText;
    }
  };

  const validate = () => {
    const validationStatus = getValidationStatus();
    const validationText = getValidationText(validationStatus);
    if (
      validationStatus ===
        validationStatusOptions.VALUE_RANGE_MISSING_START_VALUE ||
      validationStatus === validationStatusOptions.VALUE_MISSING_ERROR
    ) {
      return [true, validationText, false, ""];
    }
    if (
      validationStatus ===
        validationStatusOptions.VALUE_RANGE_MISSING_END_VALUE ||
      validationStatus === validationStatusOptions.VALUE_RANGE_ORDER_ERROR
    ) {
      return [false, "", true, validationText];
    }

    return [false, "", false, ""];
  };

  return validate();
}
