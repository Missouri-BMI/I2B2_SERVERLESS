import { QueryTerm, QueryTermGroup, TimelineEventOptions } from "models";
import { queryGroupReducer } from "../queryGroupReducer";

describe("queryGroupReducer", () => {
  const mockTerm = QueryTerm({
    path: "mock term path",
    displayName: "mock display name",
    highlightedName: "mock highlighted name",
    conceptCategory: "mock concept category",
    isLab: false,
  });
  const mockQueryTermGroup = QueryTermGroup({ id: "12345" });
  const mockQueryTermGroups = new Map([
    [mockQueryTermGroup.id, mockQueryTermGroup],
  ]);

  it("UPDATE_QUERY_GROUP_STATUS should update included/excluded status", () => {
    const payload = {
      id: mockQueryTermGroup.id,
      status: { status: "MOCK_STATUS" },
    };
    const action = { type: "UPDATE_QUERY_GROUP_STATUS", payload };
    const actual = queryGroupReducer(
      { queryTermGroups: mockQueryTermGroups },
      action
    );

    expect(actual.queryTermGroups.get(payload.id).status).toBe(payload.status);
  });

  it("UPDATE_QUERY_GROUP_OPTIONS should update specified options", () => {
    const payload = {
      id: mockQueryTermGroup.id,
      newOptions: { options: { startDate: 0 } },
    };
    const action = { type: "UPDATE_QUERY_GROUP_OPTIONS", payload };
    const actual = queryGroupReducer(
      { queryTermGroups: mockQueryTermGroups },
      action
    );

    expect(actual.queryTermGroups.get(mockQueryTermGroup.id).options).toEqual(
      payload.newOptions.options
    );
  });
  it("UPDATE_QUERY_GROUP_CONCEPTS should update the concept list for a queryTermGroup", () => {
    const concept = QueryTerm({
      path: "updated term path",
      displayName: "updated mock display name",
      highlightedName: "updated mock highlighted name",
      conceptCategory: "updated mock concept category",
      isLab: false,
    });
    const newConcepts = [concept, mockTerm];
    const payload = {
      id: mockQueryTermGroup.id,
      concepts: newConcepts,
    };
    const action = {
      type: "UPDATE_QUERY_GROUP_CONCEPTS",
      payload,
    };
    const actual = queryGroupReducer(
      { queryTermGroups: mockQueryTermGroups },
      action
    );

    expect(actual.queryTermGroups.get(payload.id).concepts).toEqual(
      payload.concepts
    );
  });
  it("UPDATE_TIMELINE_EVENT_CONCEPTS should update the concepts for Event2", () => {
    const concept = QueryTerm({
      path: "event term path",
      displayName: "event mock display name",
      highlightedName: "event mock highlighted name",
      conceptCategory: "event mock concept category",
      isLab: false,
    });
    const concepts = [concept];
    const payload = {
      concepts,
      timelineEventId: "1",
    };
    const action = {
      type: "UPDATE_TIMELINE_EVENT_CONCEPTS",
      payload,
    };
    const result = queryGroupReducer(
      {
        queryTermGroups: mockQueryTermGroups,
        timelineGroupId: mockQueryTermGroup.id,
      },
      action
    );
    expect(
      result.queryTermGroups.get(mockQueryTermGroup.id).timeline.timelineEvents[1].concepts
    ).toEqual(concepts);
  });

  it("UPDATE_TIMELINE_EVENT_CONCEPTS should update the concepts for Event1", () => {
    const concept = QueryTerm({
      path: "event term path",
      displayName: "event mock display name",
      highlightedName: "event mock highlighted name",
      conceptCategory: "event mock concept category",
      isLab: false,
    });
    const concepts = [concept];
    const payload = {
      concepts,
      timelineEventId: "0",
    };
    const action = {
      type: "UPDATE_TIMELINE_EVENT_CONCEPTS",
      payload,
    };
    const result = queryGroupReducer(
      {
        queryTermGroups: mockQueryTermGroups,
        timelineGroupId: mockQueryTermGroup.id,
      },
      action
    );
    expect(
      result.queryTermGroups.get(mockQueryTermGroup.id).timeline.timelineEvents[0].concepts
    ).toEqual(concepts);
  });

  it("UPDATE_TIMELINE_EVENT_OPTIONS should update the options for the specified event on the timeline", () => {
    const payload = {
      eventId: "0",
      newEventOptions: { startDate: 0, endDate: 1, occurrences: 0,},
    };
    const action = { type: "UPDATE_TIMELINE_EVENT_OPTIONS", payload };
    const actual = queryGroupReducer(
      {
        queryTermGroups: mockQueryTermGroups,
        timelineGroupId: mockQueryTermGroup.id,
      },
      action
    );

    expect(
      actual.queryTermGroups.get(mockQueryTermGroup.id).timeline.timelineEvents[0].options
    ).toEqual({ ...payload.newEventOptions });
  });
});
