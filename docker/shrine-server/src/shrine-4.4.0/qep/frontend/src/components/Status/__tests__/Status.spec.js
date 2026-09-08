import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';


import { Status } from '../Status';

describe('Status', () => {

  it('should render correctly', () => {

    const props = {
      className: 'mock class name',
      statusText: 'mock status text',
      resultMetadata: {
        obfuscatingParameters: {
          binSize: 1,
          stdDev: 1.33,
          noiseClamp: 3,
          lowLimit: 3
        },
        custom: null
      }
    };

    const output = shallow(
      <Status {...props} />,
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
