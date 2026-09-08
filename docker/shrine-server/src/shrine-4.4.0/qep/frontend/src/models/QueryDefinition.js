import PropTypes from "prop-types";
import { QueryTermGroup, Error } from "models";

const defaultQueryTermGroups = () => {
  const queryTermGroup = QueryTermGroup();
  return new Map([[queryTermGroup.id, queryTermGroup]]);
};

export const QueryDefinition = ({
  name = null,
  notes = null,
  faved = false,
  queryId = null,
  canSubmit = false,
  queryTermGroups = defaultQueryTermGroups(),
  error = Error(),
  hasUnsupportedFeatures = false,
  timelineGroupId = null,
} = {}) => ({
  name,
  notes,
  faved,
  queryId,
  canSubmit,
  queryTermGroups,
  hasUnsupportedFeatures,
  timelineGroupId,
  error,
});

QueryDefinition.propTypes = {
  canSubmit: PropTypes.bool,
  hasUnsupportedFeatures: PropTypes.bool,
  queryTermGroups: PropTypes.instanceOf(Map),
  error: PropTypes.shape(Error.propTypes),
  timelineGroupId: PropTypes.string,
};
