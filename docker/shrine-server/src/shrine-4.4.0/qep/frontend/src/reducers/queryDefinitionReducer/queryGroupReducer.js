import {
  UPDATE_QUERY_GROUP_OPTIONS,
  UPDATE_QUERY_GROUP_CONCEPTS,
  UPDATE_TIMELINE_EVENT_CONCEPTS,
  UPDATE_TIMELINE_EVENT_OPTIONS,
  UPDATE_QUERY_GROUP_STATUS,
  UPDATE_QUERY_GROUP_TIMELINE_LINK,
} from "actions";
import {
  QueryTermGroup,
  QueryTermGroupStatusTypes,
  Timeline,
  TimelineEventOptions,
  QueryTermGroupOptions,
  QueryTerm, TimelineLink,
} from "models";
import { TimelineEvent } from "../../models/TimelineEvent";
import {
  groupContainsDemographic,
  getTimelineGroupId,
  hasDuplicatedConcepts,
  noEmptyGroup,
  mergeQueryTermGroup,
  insertEmptyGroupIfNoneExist,
} from "./queryDefinitionReducerHelpers";

export const queryGroupReducer = (state, action) => {
  const { type, payload } = action;
  switch (type) {
    case UPDATE_QUERY_GROUP_STATUS: {
      const group = state.queryTermGroups.get(payload.id);
      const { status } = payload;

      const switchedToTimeline =
        status === QueryTermGroupStatusTypes.TIMELINE &&
        group.status !== QueryTermGroupStatusTypes.TIMELINE;
      const switchedFromTimeline =
        [
          QueryTermGroupStatusTypes.INCLUDED,
          QueryTermGroupStatusTypes.EXCLUDED,
        ].includes(status) &&
        group.status === QueryTermGroupStatusTypes.TIMELINE;

      let updatedGroup;
      if (switchedToTimeline) {
        const concepts = [];

        const timeline = Timeline();
        timeline.timelineEvents[0]= TimelineEvent({
          concepts: [...group.concepts],
          containsDemographic: group.containsDemographic,
          options: TimelineEventOptions(group.options),
        });

        updatedGroup = QueryTermGroup({
          ...group,
          concepts,
          options: QueryTermGroupOptions(),
          timeline,
          status,
        });
      } else if (switchedFromTimeline) {
        const concepts = [...group.timeline.timelineEvents[0].concepts];
        updatedGroup = QueryTermGroup({
          ...group,
          timeline: Timeline(),
          concepts,
          status,
          containsDemographic: group.timeline.timelineEvents[0].containsDemographic,
          options: QueryTermGroupOptions(group.timeline.timelineEvents[0].options),
        });
      } else {
        updatedGroup = {
          ...group,
          status,
        };
      }
      const queryTermGroups = mergeQueryTermGroup(
        payload.id,
        {
          ...updatedGroup,
          isExcluded: status === QueryTermGroupStatusTypes.EXCLUDED,
        },
        state.queryTermGroups
      );
      const timelineGroupId = getTimelineGroupId({
        queryTermGroups,
      });
      return {
        ...state,
        queryTermGroups,
        timelineGroupId,
      };
    }
    case UPDATE_QUERY_GROUP_OPTIONS: {
      const group = state.queryTermGroups.get(payload.id);

      const { newOptions } = payload;

      const updatedGroup = {
        ...group,
        ...newOptions,
      };
      const containsDemographic = groupContainsDemographic(
        updatedGroup.concepts
      );
      const queryTermGroups = mergeQueryTermGroup(
        payload.id,
        { ...updatedGroup, containsDemographic },
        state.queryTermGroups
      );
      return {
        ...state,
        queryTermGroups,
      };
    }
    case UPDATE_QUERY_GROUP_CONCEPTS: {
      const { id: groupId, concepts } = payload;
      const group = state.queryTermGroups.get(groupId);
      const updatedGroup = {
        ...group,
        concepts: concepts.map((concept) => QueryTerm(concept)),
      };

      const containsDemographic = groupContainsDemographic(
        updatedGroup.concepts
      );
      const queryTermGroups = new Map([
        ...state.queryTermGroups,
        [payload.id, QueryTermGroup({ ...updatedGroup, containsDemographic })],
      ]);

      const timelineGroupId = getTimelineGroupId({
        queryTermGroups,
      });

      return {
        ...state,
        queryTermGroups: insertEmptyGroupIfNoneExist(queryTermGroups),
        timelineGroupId,
      };
    }
    case UPDATE_TIMELINE_EVENT_CONCEPTS: {
      const { concepts, timelineEventId } = payload;
      if (hasDuplicatedConcepts(concepts)) {
        return state;
      }

      const wrappedConcepts = concepts.map((concept) => QueryTerm(concept));
      const { timelineGroupId }  = state;
      const group = state.queryTermGroups.get(timelineGroupId);
      const timeline = Timeline({
        ...group.timeline,
      });

      timeline.timelineEvents[timelineEventId] = TimelineEvent({
        ...timeline.timelineEvents[timelineEventId],
        concepts: wrappedConcepts,
        containsDemographic: groupContainsDemographic(wrappedConcepts),
      });

      let allConceptsHaveBeenRemoved = true;
      timeline.timelineEvents.forEach((elem, index) => {
        if(elem.concepts.length !== 0)
        {
          allConceptsHaveBeenRemoved = false;
        }
        else{
          //reset the timeline link
          if(index > 0)
          {
            timeline.timelineLinks[index-1] = TimelineLink();
          }
          //if this is an optional event (i.e > 2) then reset the date ranges
          //reset the date ranges
          if((index+1) > 2){
            elem.options.startDate = null;
            elem.options.endDate = null;
          }
        }
      });

      let queryTermGroups;
      if (allConceptsHaveBeenRemoved) {
        queryTermGroups = new Map(
          [...state.queryTermGroups].filter(([id]) => id !== timelineGroupId)
        );
      } else {
        const updatedGroup = {
          ...group,
          timeline,
        };

        queryTermGroups = new Map([
          ...state.queryTermGroups,
          [group.id, QueryTermGroup(updatedGroup)],
        ]);
      }

      const newTimelineGroupId = getTimelineGroupId({
        queryTermGroups,
      });
      return {
        ...state,
        queryTermGroups: insertEmptyGroupIfNoneExist(queryTermGroups),
        timelineGroupId: newTimelineGroupId,
      };
    }

    case UPDATE_QUERY_GROUP_TIMELINE_LINK: {
      const { timelineLinkIndex, timelineLink } = payload;
      const { timelineGroupId } = state;
      if (timelineGroupId === null) {
        return state;
      }

      const group = state.queryTermGroups.get(timelineGroupId);
      const timeline = Timeline({
        ...group.timeline,
      });

      timeline.timelineLinks[timelineLinkIndex] = timelineLink;

      const updatedGroup = {
        ...group,
        timeline,
      };

      const queryTermGroups = new Map([
        ...state.queryTermGroups,
        [group.id, QueryTermGroup(updatedGroup)],
      ]);

      const newTimelineGroupId = getTimelineGroupId({
        queryTermGroups,
      });
      return {
        ...state,
        queryTermGroups,
        timelineGroupId: newTimelineGroupId,
      };
    }
    case UPDATE_TIMELINE_EVENT_OPTIONS: {
      const { timelineGroupId } = state;
      if (timelineGroupId === null) {
        return state;
      }
      const group = state.queryTermGroups.get(timelineGroupId);
      const { timeline } = group;
      const { newEventOptions, eventId } = payload;
      const updatedOptions = TimelineEventOptions({
        ...group.timeline.timelineEvents[eventId].options,
        ...newEventOptions,
      });
      const updatedTimeline = Timeline({
        ...timeline,
      });
      updatedTimeline.timelineEvents[eventId].options = updatedOptions;
      const updatedGroup = QueryTermGroup({
        ...group,
        timeline: updatedTimeline,
      });
      const queryTermGroups = mergeQueryTermGroup(
        timelineGroupId,
        updatedGroup,
        state.queryTermGroups
      );
      return {
        ...state,
        queryTermGroups,
      };
    }
    default: {
      return state;
    }
  }
};
