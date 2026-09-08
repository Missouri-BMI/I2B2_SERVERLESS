import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';

import { SortHeader } from '../SortHeader';

describe('SortHeader', () => {
  const props = {
    label: 'Mock Header Label',
    id: 'mock column name',
    isFetching: false,
    onSort: jest.fn(),
    selected: false,
  };
  it('should render correctly', () => {

    const output = shallow(
      <SortHeader {...props} />,
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });
  it('It should switch the sort', () => {

    const newProps = { ...props, selected: true };
    const output = shallow(
      <SortHeader {...newProps} />,
    );
    const result = output.find('.fa-caret-up');
    expect(output.find('.fa-caret-up').length).toBe(1);
    output.simulate('click');
    expect(props.onSort).toHaveBeenCalled();
    expect(output.find('.fa-caret-up').length).toBe(0);
    expect(output.find('.fa-caret-down').length).toBe(1);
  });
});
