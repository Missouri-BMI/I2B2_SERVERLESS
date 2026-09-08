import _ from "lodash-uuid";
import PropTypes from "prop-types";

import { QueryTerm } from "./QueryTerm";
import { QueryTermGroupOptions } from "./QueryTermGroupOptions";
import { Timeline } from "./Timeline";

export const QueryTermGroupStatusTypes = {
  INACTIVE: "INACTIVE",
  INCLUDED: "INCLUDED",
  EXCLUDED: "EXCLUDED",
  TIMELINE: "TIMELINE",
};

export const QueryTermGroup = ({
  concepts = [],
  timeline = Timeline(),
  isExcluded = false,
  status = null,
  options = QueryTermGroupOptions(),
  containsDemographic = false,
  id = _.uuid(),
} = {}) => ({
  concepts,
  options,
  id,
  timeline,
  isExcluded,
  status,
  containsDemographic,
});

QueryTermGroup.propTypes = {
  isExcluded: PropTypes.bool,
  status: PropTypes.string,
  concepts: PropTypes.arrayOf(PropTypes.shape(QueryTerm.propTypes)).isRequired,
  options: PropTypes.shape(QueryTermGroupOptions.propTypes),
  id: PropTypes.string.isRequired,
  containsDemographic: PropTypes.bool,
  timeline: PropTypes.shape(Timeline.propTypes),
};
