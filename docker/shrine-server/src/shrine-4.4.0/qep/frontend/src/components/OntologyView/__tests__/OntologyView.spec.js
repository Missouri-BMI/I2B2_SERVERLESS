import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { SearchSuggestions } from "models";
import { OntologyView } from "..";

xdescribe("OntologyView ", () => {
  const getState = () => ({
    autoSuggestions: SearchSuggestions({
      suggestions: [{ title: "mock suggestion title" }],
    }),
  });
  const store = {
    getState,
    dispatch: jest.fn(),
    subscribe: jest.fn(),
  };
  const props = {
    store,
    ontology: {
      isFetching: false,
      root: [],
      error: {
        hasError: false,
      },
    },
    fetchChildren: jest.fn(),
    onSearch: jest.fn(),
    onSearchReset: jest.fn(),
  };
  const output = mount(<OntologyView {...props} />);

  //MUI Autocomplete generates a new id on every build which breaks snapshot.
  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
