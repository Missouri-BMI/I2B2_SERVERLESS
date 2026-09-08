import PropTypes from "prop-types";

import { TimelineEventOptions } from "./TimelineEventOptions";
import { QueryTerm } from "./QueryTerm";

export const TimelineEvent = ({
  concepts = [],
  isExcluded = false,
  options = TimelineEventOptions(),
  containsDemographic = false,
} = {}) => ({
  concepts,
  isExcluded,
  options,
  containsDemographic,
});

TimelineEvent.propTypes = {
  concepts: PropTypes.arrayOf(PropTypes.shape(QueryTerm.propTypes)),
  isExcluded: PropTypes.bool,
  options: PropTypes.shape(TimelineEventOptions.propTypes),
  containsDemographic: PropTypes.bool.isRequired,
};
