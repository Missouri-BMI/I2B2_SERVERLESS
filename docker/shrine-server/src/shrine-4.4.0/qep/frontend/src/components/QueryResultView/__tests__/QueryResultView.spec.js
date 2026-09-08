import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';

import { SelectedQuery } from '../../../models';
import { QueryResultView } from '..';

describe('QueryResultView', () => {
  const props = {
    selectedQuery: SelectedQuery(),
  };

  it('should render correctly', () => {
    const output = shallow(
      <QueryResultView {...props} />,
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it('should render a status message before a response from the server is received', () => {
    const newProps = {
      ...props,
      networkId: null,
      institutionResults: [],
    };
    const output = shallow(
      <QueryResultView {...props} />
    );
    expect(output.find('QueryStatus').length).toBe(0);
  });
});
