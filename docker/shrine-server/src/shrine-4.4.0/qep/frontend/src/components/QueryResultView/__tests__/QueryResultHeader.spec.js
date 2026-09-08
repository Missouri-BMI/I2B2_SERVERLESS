import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';

import { QueryResultHeader } from '../QueryResultHeader';

describe('QueryResultHeader', () => {

  it('should render correctly with the button enabled', () => {
    const props = { disableButton: false };
    const output = shallow(
      <QueryResultHeader {...props} />,
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it('should render correctly with the button disabled', () => {
    const props = { disableButton: true };
    const output = shallow(
      <QueryResultHeader {...props} />,
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
