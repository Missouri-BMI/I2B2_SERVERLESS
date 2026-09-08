// TODO-219: fix/clean-up these imports
import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import { Divider, Button, Typography, Collapse } from "@material-ui/core";
import { Alert, AlertTitle } from "@material-ui/lab";
import { UNSUPPORTED_QUERY_ALERT, local } from "../../utilities";

import { isTimelineEvent } from "models";
import {
  DroppableConceptList,
  DroppableConceptListContext,
  Confirmation,
  QueryDefinitionContext,
} from "components";
import { useScrollOnDrag } from "hooks";
import { ResearcherContext } from "pages";
import { getBaseUrl, secureFetch } from "utilities";
import { QueryTermGroupView, Includable } from "./QueryTermGroupView";
import { StartQuery } from "./StartQuery";
import { Tutorial, cancelTutorial } from "./Tutorial";

import "./QueryDefinitionView.scss";

export const QueryDefinitionView = (props) => {
  const {
    startQuery,
    queryTermGroups,
    dataDistributionTypes,
    queryName,
    hasUnsupportedFeatures,
    runButtonEnabled,
    updateQueryGroupOptions,
    updateQueryGroupConcepts,
    updateTimelineEventConcepts,
    updateTimelineEventOptions,
    updateQueryGroupTimelineLink,
    addGroupTerm,
    updateGroupTermOptionsOptions,
    updateGroupTimelineTerm,
    removeGroupTerm,
    removeAllGroupTerms,
    resetQueryDefinition,
    setUserLocalConfig,
    startTutorial,
    removeTutorial,
    timelineGroupId,
    updateQueryGroupStatus,
    networkName,
    termsOfUseText
  } = props;

  useScrollOnDrag();
  const { forceCloseTutorial } = React.useContext(ResearcherContext);
  const [confirmClearAllCriteria, setConfirmClearAllCriteria] = useState(false);
  const [
    expandGroupSettingsForTutorial,
    setExpandGroupSettingsForTutorial,
  ] = useState(false);
  const [openAlert, setOpenAlert] = useState(false);

  const fetchConceptInfo = (path) => {
    const url = `${getBaseUrl()}ontology/conceptInfo`;
    const headers = {
      "Content-Type": "application/json",
    };
    const fetchConfig = {
      headers,
      method: "POST",
      body: JSON.stringify({ path }),
    };

    const fetchedData = secureFetch(url, fetchConfig);
    return fetchedData;
  };

  const tutorialSetup = (term) => {
    const group = Array.from(queryTermGroups)[0][1];
    addGroupTerm({ id: group.id, term });
    setExpandGroupSettingsForTutorial(true);
  };

  const tutorialBreakdown = () => {
    removeTutorial();
    resetQueryDefinition();
    setExpandGroupSettingsForTutorial(false);
  };

  const generateDefaultQueryName = () => {
    const maxDefaultNameLength = 40;
    const groupsToInclude = Math.min(
      (maxDefaultNameLength - 8) / 2,
      queryTermGroups.size - 1
    );
    const totalCharactersFromConcepts =
      maxDefaultNameLength - 8 - groupsToInclude;
    const charactersToInclude = Math.floor(
      totalCharactersFromConcepts / groupsToInclude
    );

    const queryNamePrefix = Array.from(queryTermGroups.values())
      .slice(0, groupsToInclude)
      .map((el) => {
        let timelineConcepts = el.concepts;
        el.timeline.timelineEvents.forEach(event =>{
           timelineConcepts.push(...event.concepts);
        });
        return timelineConcepts[0].displayName.trim().substr(0, charactersToInclude);
      })
      .join("-");
    const timeStamp = new Date().toLocaleTimeString(undefined, {
      hour12: false,
    });
    return `${queryNamePrefix}@${timeStamp}`;
  };

  const hideConfirmationDialog = () => setConfirmClearAllCriteria(false);

  const showConfirmationDialog = () => setConfirmClearAllCriteria(true);

  const onShowTutorialAgainChange = (hideTutorial = true) => {
    setUserLocalConfig( hideTutorial );
  };

  const handleStatusChange = (groupId, status) => {
    if (status !== "INACTIVE") {
      updateQueryGroupStatus({
        id: groupId,
        status,
      });
    }
  };

  const handleOptionsChange = (groupId, opts) =>
    updateQueryGroupOptions({
      id: groupId,
      newOptions: { options: opts },
    });

  const handleConceptAddedFromGroup = (data) => {
    const { concepts, id = null } = data;
    if (isTimelineEvent(data.id)) {
      let eventId = data.id.replace("event-", "");
      updateTimelineEventConcepts({ concepts, timelineEventId: eventId });
    } else {
      updateQueryGroupConcepts({ concepts, id });
    }
  };

  useEffect(() => {
    if (hasUnsupportedFeatures) {
      setOpenAlert(true);
    }
  }, [hasUnsupportedFeatures]);

  useEffect(() => {
    if (forceCloseTutorial) {
      cancelTutorial();
    }
  }, [forceCloseTutorial]);

  return (
    <div className="QueryDefinition">
      {local.isTOUAccepted(termsOfUseText) && startTutorial && (
        <Tutorial
          setup={tutorialSetup}
          breakdown={tutorialBreakdown}
          onShowAgainChange={onShowTutorialAgainChange}
          networkName = {networkName}
        />
      )}

      <span className="title">
        <Typography className="definition-title">
          Define Inclusion and Exclusion Criteria
        </Typography>
        <Button
          disabled={queryTermGroups.size === 1}
          variant="outlined"
          className={`clear ${queryTermGroups.size === 1 ? "" : "active"}`}
          size="small"
          onClick={showConfirmationDialog}
        >
          Clear All
        </Button>
      </span>
      <Divider variant="middle" />
      <Collapse in={openAlert}>
        <Alert
          className="legacy-criteria-alert"
          severity="warning"
          onClose={() => setOpenAlert(false)}
        >
          <AlertTitle>Warning</AlertTitle>
          {UNSUPPORTED_QUERY_ALERT}
        </Alert>
      </Collapse>
      <QueryDefinitionContext.Provider
        value={{ renderGroupAsExpanded: expandGroupSettingsForTutorial }}
      >
        <div className="QueryGroups">
          <DroppableConceptListContext
            conceptListContainers={queryTermGroups}
            onConceptAddedFromGroup={handleConceptAddedFromGroup}
            removeAllGroupTerms={removeAllGroupTerms}
            timelineGroupId={timelineGroupId}
          >
            {[...queryTermGroups].map(([id, group]) => (
              <Includable>
                <DroppableConceptList
                  key={id}
                  listId={id}
                  list={group.concepts}
                  isDropDisabled={group.id === timelineGroupId}
                  onConceptAddedFromOntology={(data) => {
                    const { data: term } = data;
                    addGroupTerm({ id: group.id, term });
                  }}
                >
                  <QueryTermGroupView
                    group={group}
                    key={id}
                    updateTerm={
                      timelineGroupId !== group.id
                        ? updateGroupTermOptionsOptions
                        : updateGroupTimelineTerm
                    }
                    removeTerm={removeGroupTerm}
                    removeAllTerms={removeAllGroupTerms}
                    onOptionsChange={handleOptionsChange}
                    onStatusChange={handleStatusChange}
                    fetchConceptInfo={fetchConceptInfo}
                    timelineOptionDisabled={
                      timelineGroupId !== null && timelineGroupId !== group.id
                    }
                    updateTimelineEventConcepts={updateTimelineEventConcepts}
                    updateTimelineEventOptions={updateTimelineEventOptions}
                    updateQueryGroupTimelineLink={updateQueryGroupTimelineLink}
                  />
                </DroppableConceptList>
              </Includable>
            ))}
          </DroppableConceptListContext>

          {confirmClearAllCriteria && (
            <Confirmation
              text="Are you sure you want to clear out all the inclusion and exclusion
            criteria?"
              onOk={() => {
                resetQueryDefinition();
                hideConfirmationDialog();
                setOpenAlert(false);
              }}
              onCancel={hideConfirmationDialog}
            />
          )}
        </div>
      </QueryDefinitionContext.Provider>
      <StartQuery
        className="StartQuery"
        networkName={`${networkName}`}
        isEnabled={runButtonEnabled}
        onStartQuery={(queryName, selectedDataDistTypes) =>
          startQuery({
            queryName,
            queryNotes: "",
            queryTermGroups,
            selectedDataDistTypes,
            timelineGroupId,
          })
        }
        dataDistributionTypes={dataDistributionTypes}
        initialQueryName={queryName}
        generateDefaultQueryName={generateDefaultQueryName}
      />
    </div>
  );
};

QueryDefinitionView.defaultProps = {
  queryName: null,
  timelineGroupId: null,
};

QueryDefinitionView.propTypes = {
  queryTermGroups: PropTypes.instanceOf(Map).isRequired,
  queryName: PropTypes.string,
  runButtonEnabled: PropTypes.bool.isRequired,
  startQuery: PropTypes.func.isRequired,
  updateQueryGroupOptions: PropTypes.func.isRequired,
  updateQueryGroupConcepts: PropTypes.func.isRequired,
  addGroupTerm: PropTypes.func.isRequired,
  removeGroupTerm: PropTypes.func.isRequired,
  updateQueryGroupTimelineLink: PropTypes.func.isRequired,
  resetQueryDefinition: PropTypes.func.isRequired,
  removeAllGroupTerms: PropTypes.func.isRequired,
  setUserLocalConfig: PropTypes.func.isRequired,
  startTutorial: PropTypes.bool.isRequired,
  removeTutorial: PropTypes.func.isRequired,
  hasUnsupportedFeatures: PropTypes.bool.isRequired,
  timelineGroupId: PropTypes.string,
  networkName: PropTypes.string,
};
