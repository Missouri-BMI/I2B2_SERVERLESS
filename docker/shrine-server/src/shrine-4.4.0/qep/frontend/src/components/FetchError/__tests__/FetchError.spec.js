import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';

import FetchError from '../FetchError';

describe('FetchError', () => {
  it('should render correctly', () => {
    const error = {
      hasError: true,
      message: 'This is a mock error',
      url: 'mock url',
    };

    const output = shallow(
      <FetchError
        error={error}
      />,
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
