import PropTypes from "prop-types";

export const flagTypes = ["ANY", "NORMAL", "HIGH", "LOW"];
export const numberTypes = ["EQ", "LT", "GT", "LE", "GE"];
export const rangeTypes = ["BETWEEN"];
export const constraintTypes = [...flagTypes, ...numberTypes, ...rangeTypes];

export const TermConstraint = ({
  constraintType = "ANY",
  value = [],
  unit = ""
} = {}) => ({
  constraintType,
  value,
  unit
});

/*
  If type it is a flagType, value is null.
  If type is a numberType, value is an array of one or two numbers depending if it is BETWEEN or not.
*/
TermConstraint.PropTypes = PropTypes.shape({
  constraintType: PropTypes.oneOf(constraintTypes).isRequired,
  value: PropTypes.arrayOf(PropTypes.number),
  unit: PropTypes.string
});
