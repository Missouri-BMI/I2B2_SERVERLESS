import { QueryTerm, QueryTermGroup } from "models";
import { groupTermsReducer } from "../groupTermsReducer";

describe("queryTermsReducer", () => {
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

  it("ADD_GROUP_TERM should insert a concept into the specified queryTermGroup", () => {
    const payload = { id: mockQueryTermGroup.id, term: mockTerm };
    const action = { type: "ADD_GROUP_TERM", payload };
    const actual = groupTermsReducer(
      { queryTermGroups: mockQueryTermGroups },
      action
    );

    expect(actual.queryTermGroups.get(payload.id).concepts[0]).toEqual(
      mockTerm
    );
  });
  it("REMOVE_GROUP_TERM should remove a concept from the specified queryTermGroup", () => {
    const queryTermGroup = QueryTermGroup({
      concepts: [mockTerm],
      id: "6789",
    });
    const queryTermGroups = new Map([[queryTermGroup.id, queryTermGroup]]);
    const payload = { id: queryTermGroup.id, termPath: mockTerm.path };
    const action = { type: "REMOVE_GROUP_TERM", payload };
    const actual = groupTermsReducer({ queryTermGroups }, action);
    expect(Array.from(actual.queryTermGroups).length).toBe(0);
  });
  it("UPDATE_GROUP_TERM_OPTIONS", () => {
    const queryTermGroup = QueryTermGroup({
      concepts: [mockTerm],
      id: "6789",
    });
    const queryTermGroups = new Map([[queryTermGroup.id, queryTermGroup]]);
    const payload = {
      id: "6789",
      termPath: mockTerm.path,
      newOptions: { displayName: "updated display name" },
    };
    const action = { type: "UPDATE_GROUP_TERM_OPTIONS", payload };
    const [conceptToBeUpdated] = queryTermGroup.concepts;

    expect(conceptToBeUpdated.displayName).not.toBe(
      payload.newOptions.displayName
    );
    const actual = groupTermsReducer({ queryTermGroups }, action);
    const [updatedConcept] = actual.queryTermGroups.get(payload.id).concepts;
    expect(updatedConcept.displayName).toBe(payload.newOptions.displayName);

    console.log(queryTermGroups.get(payload.groupId));
  });

  it("REMOVE_ALL_GROUP_TERMS should actually remove the queryTermGroup, since per business logic a queryTermGroup cannot go from having concepts to empty", () => {
    const queryTermGroup = QueryTermGroup({
      concepts: [mockTerm],
      id: "6789",
    });
    const queryTermGroups = new Map([[queryTermGroup.id, queryTermGroup]]);
    const payload = { groupId: "6789" };
    const action = { type: "REMOVE_ALL_GROUP_TERMS", payload };
    const actual = groupTermsReducer({ queryTermGroups }, action);
    expect(Array.from(actual.queryTermGroups).length).toBe(0);
  });
});
