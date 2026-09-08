import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";
import { DndProvider } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";
import { CellMeasurerCache } from "react-virtualized";

import { FilteredBranch } from "../FilteredBranch";

const cache = new CellMeasurerCache({
  fixedWidth: true,
  defaultHeight: 100
});

describe("FilteredBranch", () => {
  const props = {
    concept: {
      displayName: "mock concept",
      conceptType: "Folder",
      path: "mock path",
      children: []
    },
    key: "mockKey",
    style: { width: 10, height: 10, top: 10 },
    parent: {},
    index: 1,
    toggleExpanded: jest.fn(),
    cache
  };

  const output = mount(
    <DndProvider backend={HTML5Backend}>
      <FilteredBranch {...props} />
    </DndProvider>
  );

  xit("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
