import {
  ADD_GROUP_TERM,
  UPDATE_GROUP_TERM_OPTIONS,
  UPDATE_GROUP_TIMELINE_TERM,
  REMOVE_GROUP_TERM,
  REMOVE_ALL_GROUP_TERMS,
} from "actions";
import { QueryTerm, QueryTermGroup, Timeline, TimelineEvent } from "models";
import {
  groupContainsDemographic,
  mergeQueryTermGroup,
  insertEmptyGroupIfNoneExist,
} from "./queryDefinitionReducerHelpers";

const insertTerm = (groupId, term, queryTermGroups) => {
  const noDuplicatedConcepts = (cs) =>
    cs.every((child) => child.path !== term.path);

  const queryTermGroup = queryTermGroups.get(groupId);

  if (noDuplicatedConcepts(queryTermGroup.concepts)) {
    const updatedConcepts = [...queryTermGroup.concepts, QueryTerm(term)];
    const containsDemographic = groupContainsDemographic(updatedConcepts);
    const updatedGroup = QueryTermGroup({
      ...queryTermGroup,
      concepts: updatedConcepts,
      containsDemographic,
    });

    const updatedGroups = new Map([
      ...queryTermGroups,
      [groupId, updatedGroup],
    ]);
    return { queryTermGroups: insertEmptyGroupIfNoneExist(updatedGroups) };
  }
  return { queryTermGroups };
};

const removeTerm = (groupId, termPath, queryTermGroups) => {
  const queryTermGroup = queryTermGroups.get(groupId);
  const { concepts } = queryTermGroup;
  if (concepts.length > 1) {
    const updatedConcepts = concepts.filter((term) => term.path !== termPath);
    const containsDemographic = groupContainsDemographic(updatedConcepts);
    const updatedGroup = QueryTermGroup({
      ...queryTermGroup,
      concepts: updatedConcepts,
      containsDemographic,
    });
    return {
      queryTermGroups: new Map([...queryTermGroups, [groupId, updatedGroup]]),
    };
  }

  return {
    queryTermGroups: new Map(
      [...queryTermGroups].filter(([id]) => id !== groupId)
    ),
  };
};

const updateTerm = (groupId, termPath, newOptions, queryTermGroups) => {
  const queryTermGroup = queryTermGroups.get(groupId);
  const updatedConcept = {
    ...queryTermGroup.concepts.find((c) => c.path === termPath),
    ...newOptions,
  };
  const updatedTermGroup = QueryTermGroup({
    ...queryTermGroup,
    concepts: queryTermGroup.concepts.map((concept) =>
      concept.path === termPath ? updatedConcept : concept
    ),
  });
  return {
    queryTermGroups: new Map([...queryTermGroups, [groupId, updatedTermGroup]]),
  };
};

const updateTimelineTerm = ({
  eventId,
  termPath,
  newOptions,
  queryTermGroups,
  timelineGroupId,
}) => {
  const timelineGroup = queryTermGroups.get(timelineGroupId);
  const { timeline } = timelineGroup;
  const conceptList = timeline.timelineEvents[eventId].concepts;

  const updatedConcept = {
    ...conceptList.find((concept) => concept.path === termPath),
    ...newOptions,
  };
  const updatedConceptList = conceptList.map((concept) =>
    concept.path === termPath ? updatedConcept : concept
  );

  const updatedTimeline = Timeline(timeline);
  updatedTimeline.timelineEvents[eventId].concepts = updatedConceptList;

  const updatedGroup = {
    ...timelineGroup,
    timeline: updatedTimeline,
  };

  const updatedQueryTermGroups = mergeQueryTermGroup(
    timelineGroupId,
    updatedGroup,
    queryTermGroups
  );
  return {
    queryTermGroups: updatedQueryTermGroups,
  };
};

export const groupTermsReducer = (state, action) => {
  const { type, payload } = action;
  const { queryTermGroups } = state;
  switch (type) {
    case ADD_GROUP_TERM:
      return insertTerm(payload.id, payload.term, queryTermGroups);
    case REMOVE_GROUP_TERM:
      return removeTerm(payload.id, payload.termPath, queryTermGroups);
    case UPDATE_GROUP_TERM_OPTIONS: {
      return updateTerm(
        payload.id,
        payload.termPath,
        payload.newOptions,
        queryTermGroups
      );
    }
    case UPDATE_GROUP_TIMELINE_TERM: {
      const { eventId, termPath, newOptions } = payload;
      const { timelineGroupId } = state;
      return updateTimelineTerm({
        eventId,
        termPath,
        newOptions,
        timelineGroupId,
        queryTermGroups,
      });
    }
    case REMOVE_ALL_GROUP_TERMS: {
      // Per business logic requirements, since group can't go from having concepts to empty, just remove the group.
      const groupId = payload.groupId;
      const removeGroupSinceItHasNoConcepts = ([id]) => id !== groupId;
      const filteredQueryGroupTerms = new Map(
        [...state.queryTermGroups].filter(removeGroupSinceItHasNoConcepts)
      );

      //check if group being removed is the timeline group
      let timelineGroupId = state.timelineGroupId;
      if(groupId === state.timelineGroupId){
        timelineGroupId = null;
      }

      return {
        ...state,
        timelineGroupId: timelineGroupId,
        queryTermGroups: filteredQueryGroupTerms,
      };
    }
    default:
      return queryTermGroups;
  }
};
