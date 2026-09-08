import PropTypes from "prop-types";

export const TimelineEventOptions = ({
  startDate = null,
  endDate = null,
  occurrences = 1,
} = {}) => ({
  startDate,
  endDate,
  occurrences,
});

TimelineEventOptions.propTypes = {
  startDate: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  endDate: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
};
