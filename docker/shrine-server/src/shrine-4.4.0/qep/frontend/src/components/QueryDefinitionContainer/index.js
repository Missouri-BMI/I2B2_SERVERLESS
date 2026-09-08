import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import { connect } from "react-redux";
import { bindActionCreators } from "redux";

import { Error } from "models";
import { FetchError } from "components";
import { QueryDefinitionView } from "components/QueryDefinitionView";
import { local } from "utilities";

import {
  updateQueryGroupOptions,
  updateQueryGroupConcepts,
  updateTimelineEventConcepts,
  updateTimelineEventOptions,
  addGroupTerm,
  updateGroupTermOptionsOptions,
  removeGroupTerm,
  removeAllGroupTerms,
  resetQueryDefinition,
  startQuery,
  setUserLocalConfig,
  fetchDataDistributionTypes,
  updateQueryGroupStatus,
  updateGroupTimelineTerm,
  updateQueryGroupTimelineLink
} from "actions";

const mapStateToProps = ({
  queryDefinition,
  viewMode,
  dataDistributionTypes,
  networkConfig,
}) => ({
  queryTermGroups: queryDefinition.queryTermGroups,
  runButtonEnabled: queryDefinition.canSubmit,
  queryName: queryDefinition.name,
  hasUnsupportedFeatures: queryDefinition.hasUnsupportedFeatures,
  timelineGroupId: queryDefinition.timelineGroupId,
  ...queryDefinition,
  viewMode,
  dataDistributionTypes,
  error: queryDefinition.error,
  networkName: networkConfig.name,
  termsOfUseText: networkConfig.termsOfUseText
});

const mapDispatchToProps = (dispatch) => ({
  ...bindActionCreators(
    {
      updateQueryGroupOptions,
      updateQueryGroupConcepts,
      updateTimelineEventConcepts,
      updateTimelineEventOptions,
      addGroupTerm,
      updateGroupTermOptionsOptions,
      updateGroupTimelineTerm,
      removeGroupTerm,
      removeAllGroupTerms,
      resetQueryDefinition,
      startQuery,
      setUserLocalConfig,
      updateQueryGroupStatus,
      fetchDataDistributionTypes,
      updateQueryGroupTimelineLink,
    },
    dispatch
  ),
});

export function WrappedQueryDefinitionContainer(props) {
  const {
    viewMode,
    error,
    fetchDataDistributionTypes: dispatchFetchDataDistributionTypes,
    ...otherProps
  } = props;
  const [startTutorial, setStartTutorial] = useState(false);

  const removeTutorial = () => {
    setStartTutorial(false);
  };

  useEffect(() => {
    if (!props.dataDistributionTypes.isFetching && !props.dataDistributionTypes.isLoaded) {
      dispatchFetchDataDistributionTypes();
    }
    const conf = local.getUserConfig();
    const shouldStartTutorial =
      (!conf || !conf.hideTutorial) &&
      viewMode.justLoggedIn &&
      !error.hasError &&
      !startTutorial;
    setStartTutorial(shouldStartTutorial);
    return () => {
      setStartTutorial(false);
    };
  }, []);

  const queryDefinitionViewProps = {
    ...otherProps,
    startTutorial,
    removeTutorial,
    error,
  };
  return error.hasError ? (
    <FetchError error={error} />
  ) : (
    <QueryDefinitionView {...queryDefinitionViewProps} />
  );
}

WrappedQueryDefinitionContainer.propTypes = {
  viewMode: PropTypes.shape({
    justLoggedIn: PropTypes.bool,
  }).isRequired,
  error: PropTypes.shape(Error.propTypes).isRequired,
  dataDistributionTypes: PropTypes.shape({
    isFetching: PropTypes.bool,
    isLoaded: PropTypes.bool,
  }).isRequired
};

export const QueryDefinitionContainer = connect(
  mapStateToProps,
  mapDispatchToProps
)(React.memo(WrappedQueryDefinitionContainer));
