import PropTypes from "prop-types";

export const GroupLinkType = {
  SameEncounter: "SameEncounter",
  SameInstance: "SameInstance",
};

export const QueryTermGroupOptions = ({
  startDate = null,
  endDate = null,
  occurrences = 1,
  linkedBy = null
} = {}) => ({
  startDate,
  endDate,
  occurrences,
  linkedBy
});

QueryTermGroupOptions.propTypes = {
  startDate: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  endDate: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  occurrences: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  linkedBy: PropTypes.string
};
