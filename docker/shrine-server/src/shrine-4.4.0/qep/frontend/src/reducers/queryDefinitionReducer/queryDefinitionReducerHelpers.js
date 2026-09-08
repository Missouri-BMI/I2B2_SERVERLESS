import { QueryTermGroup, QueryTermGroupStatusTypes } from "models";

export const isDemographicConcept = ({ conceptCategory }) =>
  conceptCategory && conceptCategory.toUpperCase().includes("DEMOGRAPHIC");
export const groupContainsDemographic = (concepts) =>
  concepts.some((concept) => isDemographicConcept(concept));

export const hasDuplicatedConcepts = (conceptList) => {
  const conceptSet = new Set(conceptList.map((concept) => concept.path));
  return conceptSet.size !== conceptList.length;
};

export const noEmptyGroup = (groups) =>
  Array.from(groups.values()).every(
    (group) =>
      group.concepts.length !== 0 ||
      group.timeline.timelineEvents.filter(timelineEvent => timelineEvent.concepts.length !== 0).length > 0
  );

export const getTimelineGroupId = ({ queryTermGroups }) => {
  const [timelineGroup = null] = Array.from(queryTermGroups.values()).filter(
    ({ status }) => status === QueryTermGroupStatusTypes.TIMELINE
  );
  return timelineGroup ? timelineGroup.id : null;
};

export const mergeQueryTermGroup = (id, queryTermGroup, queryTermGroupMap) =>
  new Map([...queryTermGroupMap, [id, QueryTermGroup({ ...queryTermGroup })]]);

export const insertEmptyGroupIfNoneExist = (queryTermGroups) => {
  let updatedGroups = queryTermGroups;
  if (noEmptyGroup(queryTermGroups)) {
    const newEmptyGroup = QueryTermGroup();
    updatedGroups = new Map([
      ...queryTermGroups,
      [newEmptyGroup.id, newEmptyGroup],
    ]);
  }
  return updatedGroups;
};
