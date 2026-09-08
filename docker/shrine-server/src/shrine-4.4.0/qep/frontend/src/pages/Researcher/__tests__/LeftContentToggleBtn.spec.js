import React from 'react';
import { shallow } from 'enzyme';
import { shallowToJson } from 'enzyme-to-json';

import { LeftContentToggleBtn } from '../LeftContentToggleBtn';

describe('LeftContentToggleBtn', () => {
  it('should render correctly', () => {

    const output = shallow(
      <LeftContentToggleBtn 
        open={true}
        setOpen={jest.fn()}
      />,
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it('Should update the open state when clicked', () => {
    let open = false;
    const setOpen = jest.fn((setting) => { open = setting; });
    const output = shallow(
      <LeftContentToggleBtn 
        open={open}
        setOpen={setOpen}
      />,
    );
    output.simulate('click');
    expect(open).toBe(true);
  })
});
