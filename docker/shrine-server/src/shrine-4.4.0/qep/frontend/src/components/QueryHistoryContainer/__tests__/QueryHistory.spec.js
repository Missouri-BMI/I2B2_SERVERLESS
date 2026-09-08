import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';

import { QueryHistory } from '../QueryHistory';

describe('QueryHistory', () => {
  const props = {
    results: [],
    onScrollReachedBottom: jest.fn(),
    isFetching: false,
    selectedResultId: 123,
    onSort: jest.fn(),
    loadResult: jest.fn(),
    favTexts: {
      queryFavingInstructions: "one",
      favingIconInstructions: "two",
      favPlaceholderText: "three"
    }
  };
  it('should render correctly', () => {

    const output = shallow(
      <QueryHistory {...props} />,
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
