import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';

import { SelectedQuery, QueryResult } from '../../../models';
import { QueryStatus } from '../QueryStatus';

describe('QueryStatus', () => {



  it('should render correctly', () => {

    const props = {
      selectedQuery: SelectedQuery(),
      handleErrorStatusClicked: jest.fn(),
    };

    const output = shallow(
      <QueryStatus {...props} />,
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it('should render an error on submission error', () => {
    const props = {
      selectedQuery: SelectedQuery({ queryResult: QueryResult({status: 'submission error' })}),
      handleErrorStatusClicked: jest.fn(),
    };

    const output = shallow(
      <QueryStatus {...props} />,
    );

    expect(output.find('.QueryError').length).toBe(1);
  });

  it('should render an error on network error', () => {
    const props = {
      selectedQuery: SelectedQuery({ queryResult: QueryResult({status: 'network error' })}),
      handleErrorStatusClicked: jest.fn(),
    };

    const output = shallow(
      <QueryStatus {...props} />,
    );

    expect(output.find('.QueryError').length).toBe(1);
  });

  it('Should render success when query has run without error', () => {
    const props = {
      selectedQuery: SelectedQuery({ queryResult: QueryResult({status: 'mock success status' })}),
      handleErrorStatusClicked: jest.fn(),
    };

    const output = shallow(
      <QueryStatus {...props} />,
    );

    expect(output.find('.QueryNonError').length).toBe(1);
  });

});
