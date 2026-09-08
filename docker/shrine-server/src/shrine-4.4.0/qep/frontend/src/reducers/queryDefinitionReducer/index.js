import { defaultState } from "defaultState";
import {
  REMOVE_QUERY_GROUP,
  UPDATE_QUERY_GROUP_OPTIONS,
  UPDATE_TIMELINE_EVENT_OPTIONS,
  UPDATE_QUERY_GROUP_CONCEPTS,
  UPDATE_TIMELINE_EVENT_CONCEPTS,
  UPDATE_QUERY_GROUP_TIMELINE_LINK,
  ADD_GROUP_TERM,
  UPDATE_GROUP_TERM_OPTIONS,
  UPDATE_GROUP_TIMELINE_TERM,
  REMOVE_GROUP_TERM,
  REMOVE_ALL_GROUP_TERMS,
  RELOAD_QUERY,
  RELOAD_QUERY_SUCCEEDED,
  START_QUERY,
  START_QUERY_FAILED,
  RESET_QUERY_DEFINITION,
  UPDATE_QUERY_GROUP_STATUS,
} from "actions";
import { clearOnLogout, validateDates, validateLabValues } from "utilities";
import {
  QueryDefinition,
  QueryTerm,
  QueryTermGroup,
  QueryTermGroupStatusTypes,
  TermConstraint,
  Timeline,
  Error as SHRINEError,
} from "models";

import { groupTermsReducer } from "./groupTermsReducer";
import { queryGroupReducer } from "./queryGroupReducer";

import { groupContainsDemographic } from "./queryDefinitionReducerHelpers";

const wrapConcepts = ({ concepts }) =>
  concepts.map((_) =>
    QueryTerm({ ..._, constraint: _.constraint || TermConstraint() })
  );

const reloadQuery = (queryDefinition, reloadAction) => {
  const {
    name,
    queryId,
    conceptGroups,
    hasUnsupportedFeatures,
    timeline,
  } = reloadAction.payload;
  const groupsMap = new Map();

  let timelineGroupId = null;
  if (timeline !== null) {
    const updatedTimeline = Timeline({
      ...timeline,
    });

    updatedTimeline.timelineEvents.forEach(timelineEvent => {
      let concepts = wrapConcepts(timelineEvent);
      timelineEvent.concepts = concepts;
      timelineEvent.containsDemographic =  groupContainsDemographic(concepts);
    });


    const timelineGroup = QueryTermGroup({
      timeline: updatedTimeline,
      status: QueryTermGroupStatusTypes.TIMELINE,
    });
    timelineGroupId = timelineGroup.id;
    groupsMap.set(timelineGroupId, timelineGroup);
  }

  const queryTermGroups = conceptGroups.reduce((accMap, curQueryGroup) => {
    const concepts = wrapConcepts(curQueryGroup);
    const queryTermGroup = QueryTermGroup({
      ...curQueryGroup,
      concepts,
      status: curQueryGroup.isExcluded
        ? QueryTermGroupStatusTypes.EXCLUDED
        : QueryTermGroupStatusTypes.INCLUDED,
    });
    accMap.set(queryTermGroup.id, queryTermGroup);
    return accMap;
  }, groupsMap);

  const emptyGroup = QueryTermGroup();
  queryTermGroups.set(emptyGroup.id, emptyGroup);

  return {
    ...queryDefinition,
    name,
    queryId,
    queryTermGroups,
    hasUnsupportedFeatures,
    timelineGroupId,
  };
};

export const validateOptions = (group) => {
  const { containsDemographic, options } = group;
  const { startDate, endDate, occurrences = 1 } = options;
  const { validStartDate, validEndDate, validDateRange } = validateDates(
    startDate,
    endDate
  );

  const optionSelected = !!startDate || !!endDate || occurrences !== 1;
  if (containsDemographic && optionSelected) {
    return false;
  }

  const noStartDate = startDate === null;
  const noEndDate = endDate === null;
  const validLowerBound = validStartDate && noEndDate;
  const validUpperBound = validEndDate && noStartDate;
  const noDates = noStartDate && noEndDate;
  const validDates =
    noDates || validLowerBound || validUpperBound || validDateRange;
  const validOccurrences = occurrences > 0;

  return Boolean(validDates && validOccurrences);
};

export const validateConceptConstraint = ({ constraintType, value, unit }) => {
  const [isLowValueError, _, isHighValueError, __] = validateLabValues(
    constraintType,
    value[0],
    value[1],
    unit
  );
  return !(isLowValueError || isHighValueError);
};

const areQueryTermGroupsValid = (queryTermGroups) => {
  const allGroupOptionsValid = queryTermGroups.every((group) =>
    validateOptions(group)
  );
  const allConceptsValid = queryTermGroups.every((group) =>
    group.concepts.every((concept) =>
      validateConceptConstraint(concept.constraint)
    )
  );

  return allGroupOptionsValid && allConceptsValid;
};

const isTimelineValid = (timelineGroup) => {
  const { timeline } = timelineGroup;
  if (timeline === null) {
    return true;
  }


  return (
    timeline.timelineEvents[0].concepts.length > 0 &&
    timeline.timelineEvents[1].concepts.length > 0 &&
    timeline.timelineEvents.reduce((concepts, timelineEvent) => concepts.concat(...timelineEvent.concepts), [])
      .every((concept) => validateConceptConstraint(concept.constraint)) &&
    timeline.timelineEvents.every(timelineEvent => validateOptions(timelineEvent)) &&
    timeline.timelineLinks.every(timelineLink => isTimelineLinkValid(timelineLink)))
};

const isTimelineLinkValid = (timelineLink) => {
  let isValid = false;

  let primaryTimeSpan = timelineLink.BasicTimelineLink.primaryTimeSpan;
  let secondaryTimeSpan = timelineLink.BasicTimelineLink.secondaryTimeSpan;

  if ( primaryTimeSpan == null && secondaryTimeSpan == null) {
    isValid = true;
  } else if ( primaryTimeSpan != null && secondaryTimeSpan == null && primaryTimeSpan.value >= 0) {
    isValid = true;
  }
  else if ( primaryTimeSpan != null && secondaryTimeSpan != null && primaryTimeSpan.value >= 0
    && secondaryTimeSpan.value >= 0) {
    isValid = true;
  }

  return isValid;
};

const canSubmit = (state) => {
  const queryTermGroups = Array.from(state.queryTermGroups.values()).filter(
    (group) => group.id !== state.timelineGroupId
  );
  const timelineGroup = state.queryTermGroups.get(state.timelineGroupId);

  const someNonEmptyGroup = queryTermGroups.some(
    (group) => group.concepts.length > 0
  );

  if (!timelineGroup && someNonEmptyGroup) {
    return areQueryTermGroupsValid(queryTermGroups);
  }
  if (timelineGroup && !someNonEmptyGroup) {
    return isTimelineValid(timelineGroup);
  }

  return (
    someNonEmptyGroup &&
    areQueryTermGroupsValid(queryTermGroups) &&
    isTimelineValid(timelineGroup)
  );
};

export const queryDefinitionReducer = (
  state = defaultState.queryDefinition,
  action
) => {
  const { type } = action;
  let nextState;

  switch (type) {
    case RELOAD_QUERY:
    case RESET_QUERY_DEFINITION:
      nextState = QueryDefinition();
      break;
    case REMOVE_QUERY_GROUP:
    case UPDATE_QUERY_GROUP_CONCEPTS:
    case UPDATE_QUERY_GROUP_OPTIONS:
    case UPDATE_TIMELINE_EVENT_CONCEPTS:
    case UPDATE_TIMELINE_EVENT_OPTIONS:
    case UPDATE_QUERY_GROUP_TIMELINE_LINK:
    case UPDATE_QUERY_GROUP_STATUS:
      nextState = queryGroupReducer(state, action);
      break;
    case ADD_GROUP_TERM:
    case UPDATE_GROUP_TERM_OPTIONS:
    case UPDATE_GROUP_TIMELINE_TERM:
    case REMOVE_GROUP_TERM:
    case REMOVE_ALL_GROUP_TERMS:
      nextState = QueryDefinition({
        ...state,
        ...groupTermsReducer(state, action),
      });
      break;
    case RELOAD_QUERY_SUCCEEDED:
      nextState = reloadQuery(state, action);
      break;
    case START_QUERY_FAILED: {
      const { status, statusText, url } = action.payload.response;
      const error = SHRINEError({
        hasError: true,
        message: `${status} ${statusText}`,
        url,
      });
      nextState = {
        ...state,
        error,
      };
      break;
    }
    default:
      nextState = state;
      break;
  }
  const nextQueryDefinition = QueryDefinition({
    ...nextState,
    canSubmit: canSubmit(nextState),
  });
  return clearOnLogout(
    action.type,
    defaultState.queryDefinition,
    nextQueryDefinition
  );
};
