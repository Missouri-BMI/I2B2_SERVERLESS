import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';

import { ClickableStatus } from '../ClickableStatus';

describe('ClickableStatus', () => {

  const props = {
    className: 'mock class name',
    statusText: 'mock status text',
  };
  const output = shallow(
    <ClickableStatus {...props} />,
  );

  it('should render correctly', () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
