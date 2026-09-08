import React from "react";
import PropTypes from "prop-types";
import { DragDropContext } from "react-beautiful-dnd";
import { isTimelineEvent} from "models";

export const getConceptList = (conceptListContainers, id, timelineGroupId) => {
  if (isTimelineEvent(id)) {
    const container = conceptListContainers.get(timelineGroupId);
    const { timeline } = container;

    let eventId = id.replace("event-", "");
    return timeline.timelineEvents[eventId].concepts;
  }

  return conceptListContainers.get(id).concepts;
};

export const DroppableConceptListContext = (props) => {
  const {
    conceptListContainers,
    onConceptAddedFromGroup,
    removeAllGroupTerms,
    children,
    timelineGroupId,
  } = props;

  const moveBetweenLists = (
    destinationList,
    sourceList,
    movedConcept,
    destination,
    source
  ) => {
    const destinationConcepts = [...destinationList];
    destinationConcepts.splice(destination.index, 0, movedConcept);
    onConceptAddedFromGroup({
      id: destination.droppableId,
      concepts: destinationConcepts,
    });

    if (sourceList.length === 1 && !isTimelineEvent(source.droppableId)) {
      removeAllGroupTerms({ groupId: source.droppableId });
    } else {
      const sourceConcepts = [...sourceList];
      sourceConcepts.splice(source.index, 1);
      onConceptAddedFromGroup({
        id: source.droppableId,
        concepts: sourceConcepts,
      });
    }
  };

  const moveWithinList = (
    listId,
    list,
    movedConcept,
    sourceIndex,
    destinationIndex
  ) => {
    const concepts = [...list];
    concepts[sourceIndex] = concepts[destinationIndex];
    concepts[destinationIndex] = movedConcept;
    onConceptAddedFromGroup({
      id: listId,
      concepts,
    });
  };

  const onDragEnd = (result) => {
    const { destination, source } = result;
    const invalidDrag = !destination;

    const locationDidNotChange =
      destination.droppableId === source.droppableId &&
      destination.index === source.index;
    const sourceConceptList = getConceptList(
      conceptListContainers,
      source.droppableId,
      timelineGroupId
    );

    const destinationConceptList = getConceptList(
      conceptListContainers,
      destination.droppableId,
      timelineGroupId
    );

    const movedConcept = sourceConceptList[source.index];
    const conceptAlreadyInDestination = destinationConceptList.some(
      (concept) => concept.path === movedConcept.path
    );

    if (invalidDrag || locationDidNotChange) {
      return;
    }

    if (source.droppableId === destination.droppableId) {
      moveWithinList(
        source.droppableId,
        sourceConceptList,
        movedConcept,
        source.index,
        destination.index
      );
    } else if (!conceptAlreadyInDestination) {
      moveBetweenLists(
        destinationConceptList,
        sourceConceptList,
        movedConcept,
        destination,
        source
      );
    }
  };

  return (
    <div className="DroppableConceptListContext">
      <DragDropContext onDragEnd={onDragEnd}>{children}</DragDropContext>
    </div>
  );
};

DroppableConceptListContext.propTypes = {
  conceptListContainers: PropTypes.instanceOf(Map).isRequired,
  timelineGroupId: PropTypes.string.isRequired,
  onConceptAddedFromGroup: PropTypes.func.isRequired,
  removeAllGroupTerms: PropTypes.func.isRequired,
  children: PropTypes.element.isRequired,
};
