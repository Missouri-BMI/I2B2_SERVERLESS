import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';

import { MainContent } from '../MainContent';

describe('MainContent', () => {
  it('should render correctly', () => {

    const output = shallow(
      <MainContent />,
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
