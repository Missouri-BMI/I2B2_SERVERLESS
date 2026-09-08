import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';

import { InstitutionResult } from '../../../models';
import { InstitutionResultStatusList } from '../InstitutionResultStatusList';

describe('InstitutionResultStatusList', () => {

  const props = {
    institutionResults: [InstitutionResult()],
  };
  const output = shallow(
    <InstitutionResultStatusList {...props} />,
  );

  it('should render correctly', () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
