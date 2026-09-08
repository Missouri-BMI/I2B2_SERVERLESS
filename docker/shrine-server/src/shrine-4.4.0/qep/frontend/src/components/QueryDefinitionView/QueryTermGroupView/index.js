import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import { Container } from "@material-ui/core";

import { QueryTermGroupStatusTypes, QueryTermGroup } from "models";
import GroupHeader from "./GroupHeader";
import GroupBody from "./GroupBody";
import { Confirmation } from "components";
import "./QueryTermGroupView.scss";

export { Includable } from "./Includable";

export const QueryTermGroupView = ({
  group,
  updateTerm,
  removeTerm,
  removeAllTerms,
  onOptionsChange,
  onStatusChange,
  fetchConceptInfo,
  timelineOptionDisabled,
  updateTimelineEventConcepts,
  updateTimelineEventOptions,
  updateQueryGroupTimelineLink,
}) => {
  const [status, setStatus] = useState(QueryTermGroupStatusTypes.INACTIVE);
  const [showDeleteTimelineDialog, setShowDeleteTimelineDialog] = useState(false);

  const hasChildren = group.concepts.length > 0 || (group.timeline.timelineEvents.length > 0 && group.timeline.timelineEvents[0].concepts.length > 0) ||  (group.timeline.timelineEvents.length > 1 && group.timeline.timelineEvents[1].concepts.length > 0);
  const isActive = status !== QueryTermGroupStatusTypes.INACTIVE;
  const activeClass = isActive ? "active" : "";

  let containerClass = "";
  if (status === QueryTermGroupStatusTypes.TIMELINE) {
    containerClass = "timeline";
  } else if (status === QueryTermGroupStatusTypes.EXCLUDED) {
    containerClass = "excluded";
  }

  const handleRadioChange = (e) => {
    const { value } = e.currentTarget;
    setStatus(value);
  };

  const onDeleteTermClicked = (termKey) => {
    removeTerm({ id: group.id, termPath: termKey });
  };

  const onClearAllTermsClicked = () => {
    if(status === QueryTermGroupStatusTypes.TIMELINE){
      setShowDeleteTimelineDialog(true);
    }else{
      removeAllTerms({ groupId: group.id });
    }
  };

  const onDeleteTimeline = () => {
    removeAllTerms({ groupId: group.id });
    hideDeleteTimelineDialog();
  };

  const hideDeleteTimelineDialog = () => setShowDeleteTimelineDialog(false);

  useEffect(() => {
    const termWasDraggedBeforeRadioClicked =
      group.concepts.length > 0 &&
      status === QueryTermGroupStatusTypes.INACTIVE;

    if (termWasDraggedBeforeRadioClicked) {
      setStatus(QueryTermGroupStatusTypes.INCLUDED);
    }
  }, [group.concepts.length]);

  useEffect(() => {
    onStatusChange(group.id, status);
  }, [status]);

  useEffect(() => {
    if (group.status && group.status !== status) {
      setStatus(group.status);
    }
  }, [group.status]);

  return (
    <Container className={`QueryTermGroupView ${containerClass} tutorialStep2`}>
      <GroupHeader
        onRadioChange={handleRadioChange}
        status={status}
        activeClass={activeClass}
        onClearAllTermsClicked={onClearAllTermsClicked}
        hasChildren={hasChildren}
        timelineOptionDisabled={timelineOptionDisabled}
        group={group}
        onOptionsChange={onOptionsChange}
      />
      <GroupBody
        status={status}
        onDeleteTermClicked={onDeleteTermClicked}
        updateTerm={updateTerm}
        group={group}
        hasChildren={hasChildren}
        isActive={isActive}
        activeClass={activeClass}
        onOptionsChange={onOptionsChange}
        fetchConceptInfo={fetchConceptInfo}
        updateTimelineEventConcepts={updateTimelineEventConcepts}
        updateTimelineEventOptions={updateTimelineEventOptions}
        updateQueryGroupTimelineLink={updateQueryGroupTimelineLink}
      />

      {showDeleteTimelineDialog && <Confirmation
        text="Are you sure you want to delete this timeline?"
        onOk={onDeleteTimeline}
        onCancel={hideDeleteTimelineDialog}
      />}

    </Container>
  );
};

QueryTermGroupView.propTypes = {
  group: PropTypes.shape(QueryTermGroup.propTypes).isRequired,
  updateTerm: PropTypes.func.isRequired,
  removeTerm: PropTypes.func.isRequired,
  onOptionsChange: PropTypes.func.isRequired,
  onStatusChange: PropTypes.func.isRequired,
  removeAllTerms: PropTypes.func.isRequired,
  fetchConceptInfo: PropTypes.func.isRequired,
  timelineOptionDisabled: PropTypes.bool.isRequired,
  updateTimelineEventConcepts: PropTypes.func.isRequired,
  updateTimelineEventOptions: PropTypes.func.isRequired,
};
