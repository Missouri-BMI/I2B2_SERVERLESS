import { getTimelineGroupId } from "../queryDefinitionReducerHelpers";

describe("queryDefinitionReducerHelpers", () => {
  it("getTimelineGroupId should return true if queryTermGroups contains a group with a timeline", () => {
    const queryTermGroups = new Map([
      ["groupId1", { id: "groupId1", status: "TIMELINE" }],
    ]);
    const actualResult = getTimelineGroupId({
      queryTermGroups,
    });
    expect(actualResult).toBe("groupId1");
  });

  it("getTimelineGroupId should return false if queryTermGroups does not contain a group with a timeline", () => {
    const queryTermGroups = new Map([
      ["groupId1", { id: "groupId1", timeline: null }],
    ]);
    const actualResult = getTimelineGroupId({
      queryTermGroups,
    });

    expect(actualResult).toBe(null);
  });
});
