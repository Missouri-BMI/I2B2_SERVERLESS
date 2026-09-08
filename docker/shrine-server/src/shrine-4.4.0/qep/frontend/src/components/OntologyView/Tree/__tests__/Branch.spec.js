import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";
import { DndProvider } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";

import Branch from "../Branch";

describe("Branch", () => {
  const props = {
    concept: {
      displayName: "mock concept",
      conceptType: "Folder",
      path: "mock path",
      children: []
    },
    fetchChildren: jest.fn(),
    nodeId: "mock node id",
    expandNode: jest.fn(),
    collapseNode: jest.fn()
  };

  const output = mount(
    <DndProvider backend={HTML5Backend}>
      <Branch {...props} />
    </DndProvider>
  );

  xit("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
