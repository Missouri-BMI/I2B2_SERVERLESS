import React from 'react';
import { mount } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';
import {local} from "../../../utilities";

import { WrappedTermsOfUse } from '..';

describe('TermsOfUse', () => {
  it('should not display if user is not authenticated', () => {

    const props = {
      dispatch: jest.fn(),
      user: {
        isAuthenticated: false
      },
      networkConfig: {
        termsOfUseText: "This is the terms of use text",
      }
    };

    const output = mount(<WrappedTermsOfUse {...props} />);

    expect(shallowToJson(output)).toMatchSnapshot();
  });


  it('should display terms of use if user is authenticated', () => {
    const props = {
      dispatch: jest.fn(),
      user: {
        isAuthenticated: true
      },
      networkConfig: {
        termsOfUseText: "This is the terms of use text",
      }
    };

    const output = mount(<WrappedTermsOfUse {...props} />);

    expect(output.find('.tou-text').length).toBe(1);
  });

  it('should not display terms of use if user already accepted', () => {

    const props = {
      dispatch: jest.fn(),
      user: {
        isAuthenticated: true
      },
      networkConfig: {
        termsOfUseText: "This is the terms of use text",
      }
    };

    local.localKey = "mock session key";
    local.setTOUAccepted("This is the terms of use text");

    const output = mount(<WrappedTermsOfUse {...props} />);

    expect(output.find('.tou-text').length).toBe(0);
  });

  it('should display terms of use if user already accepted but terms of use has changed', () => {

    const props = {
      dispatch: jest.fn(),
      user: {
        isAuthenticated: true
      },
      networkConfig: {
        termsOfUseText: "This is the new terms of use text",
      }
    };

    const output = mount(<WrappedTermsOfUse {...props} />);

    expect(output.find('.tou-text').length).toBe(1);
  });
});
