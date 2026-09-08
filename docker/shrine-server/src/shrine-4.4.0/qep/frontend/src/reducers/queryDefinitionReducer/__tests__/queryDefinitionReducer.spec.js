import { QueryDefinition, QueryTerm, QueryTermGroup } from "models";
import { queryDefinitionReducer } from "..";

describe("queryDefinitionReducer", () => {
  const mockQueryTermGroup = QueryTermGroup({ id: "12345" });
  const mockQueryTermGroups = new Map([
    [mockQueryTermGroup.id, mockQueryTermGroup],
  ]);
  const mockQueryDefinition = QueryDefinition({
    queryTermGroups: mockQueryTermGroups,
  });

  it("Should yield a default state if none is provided", () => {
    const action = { type: "UKNOWN_ACTION" };

    const expected = mockQueryDefinition;
    const actual = queryDefinitionReducer(
      QueryDefinition({ queryTermGroups: mockQueryTermGroups }),
      action
    );

    // default state values.
    expect(actual).toEqual(expected); // see below
  });

  it("should reset query definition", () => {
    const mockTerm = QueryTerm({
      path: "mock term path",
      displayName: "mock display name",
      highlightedName: "mock highlighted name",
      conceptCategory: "mock concept category",
      isLab: false,
    });
    const payload = { term: mockTerm, id: mockQueryTermGroup.id };
    const action = { type: "ADD_GROUP_TERM", payload };
    const result = queryDefinitionReducer(mockQueryDefinition, action);
    const resultGroups = Array.from(result.queryTermGroups);
    expect(resultGroups.length).toEqual(2);

    const [[, firstGroup]] = resultGroups;
    expect(firstGroup.concepts[0]).toEqual(mockTerm);
    const emptyQueryDefinition = queryDefinitionReducer(result, {
      type: "RESET_QUERY_DEFINITION",
    });
    expect(
      Array.from(emptyQueryDefinition.queryTermGroups.values()).length
    ).toBe(1);

    expect(
      Array.from(emptyQueryDefinition.queryTermGroups)[0][1].concepts.length
    ).toBe(0);
  });
});
