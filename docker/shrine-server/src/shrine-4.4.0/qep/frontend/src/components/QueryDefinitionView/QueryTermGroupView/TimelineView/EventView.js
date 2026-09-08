import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import { Grid } from "@material-ui/core";

import { DroppableConceptList } from "components";
import QueryTermViewList from "../QueryTermViewList";
import GroupInstructionsPanel from "../GroupInstructionsPanel";
import { TimelineInstructions } from "./TimelineInstructions";
import { ConceptListOptions } from "../ConceptListOptions";
import "./EventView.scss";

export default function EventView({
  event,
  eventId,
  updateTimelineEventConcepts,
  updateTimelineEventOptions,
  fetchConceptInfo,
  updateTerm,
  header,
  optional
}) {
  const [conceptsMissingError, setConceptsMissingError] = useState(false);
  const handleDeleteTermClicked = (path) => {
    const editedTimelineConcepts = event.concepts.filter(
      (concept) => concept.path !== path
    );
    setConceptsMissingError(editedTimelineConcepts.length === 0);
    updateTimelineEventConcepts({
      timelineEventId: eventId,
      concepts: editedTimelineConcepts,
    });
  };

  const handleOptionsChange = (startDate, endDate, occurrences) => {
    const newEventOptions = { startDate, endDate, occurrences };
    updateTimelineEventOptions({ newEventOptions, eventId });
  };

  const className =
    event.concepts.length > 0 ? "EventView has-children" : "EventView";

  const handleConceptAddedFromOntology = (data) => {
    const { data: concept } = data;
    const concepts = [...event.concepts, concept];
    updateTimelineEventConcepts({
      timelineEventId: eventId,
      concepts,
    });
  };

  const handleUpdateTerm = (termPath, newOptions) => {
    updateTerm({ eventId, termPath, newOptions });
  };

  useEffect(() => {
    if (event.concepts.length > 0) {
      setConceptsMissingError(false);
    }
  }, [event.concepts]);
  return (
    <DroppableConceptList
      key={eventId}
      listId={"event-" + eventId}
      list={event.concepts}
      onConceptAddedFromOntology={handleConceptAddedFromOntology}
    >
      <div className={className}>
        <Grid xs={12} item className="event-content">
          <div className="header-container">
            <div className="header">{header}</div>
          </div>
          <QueryTermViewList
            terms={event.concepts}
            onDeleteTermClicked={handleDeleteTermClicked}
            updateTerm={handleUpdateTerm}
            fetchConceptInfo={fetchConceptInfo}
          />

          <GroupInstructionsPanel hasChildren={event.concepts.length > 0}>
            <TimelineInstructions eventId={eventId+1} isOptional={optional} />
          </GroupInstructionsPanel>
          <div className="header-container">
            {!optional && conceptsMissingError && (
              <div className="header error">at least 1 concept is required</div>
            )}
          </div>
        </Grid>
        <ConceptListOptions
          containsDemographic={event.containsDemographic}
          queryTermGroupOptions={event.options}
          includeOccurrences={false}
          onChange={handleOptionsChange}
        />
      </div>
    </DroppableConceptList>
  );
}

EventView.propTypes = {
  event: PropTypes.shape({
    options: PropTypes.shape({}),
    containsDemographic: PropTypes.bool,
    concepts: PropTypes.arrayOf(PropTypes.shape({})),
  }).isRequired,
  eventId: PropTypes.string.isRequired,
  updateTerm: PropTypes.func.isRequired,
  updateTimelineEventConcepts: PropTypes.func.isRequired,
  updateTimelineEventOptions: PropTypes.func.isRequired,
  fetchConceptInfo: PropTypes.func.isRequired,
  header: PropTypes.string.isRequired,
};
