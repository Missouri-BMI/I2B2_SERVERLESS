// Jest Snapshot v1, https://goo.gl/fbAQLP

// exports[`QueryNameField should render correctly 1`] = `
// <ValidatedTextField
//   InputProps={
//     Object {
//       "endAdornment": <ForwardRef(WithStyles)
//         position="end"
//       >
//         <ForwardRef(WithStyles)
//           arrow={true}
//           classes={
//             Object {
//               "tooltip": "shrine-tooltip",
//             }
//           }
//           title={
//             <ForwardRef(WithStyles)>
//               Autogenerate name
//             </ForwardRef(WithStyles)>
//           }
//         >
//           <ForwardRef(WithStyles)
//             className="generate fa fa-bolt"
//             role="button"
//           />
//         </ForwardRef(WithStyles)>
//       </ForwardRef(WithStyles)>,
//     }
//   }
//   allowLeadingSpace={false}
//   invalidCharsRegex="[!|^|\`\~]|(<script\\\\s)|(</?script>)"
//   maxCharacters={10}
//   maxTextLength={250}
//   name="Test Field"
//   onTextChange={[Function]}
//   required={true}
// />
// `;

import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import QueryNameField from "../QueryNameField";

describe("QueryNameField", () => {
  const props = {
    onTextChange: () => {},
    name: "Test Field",
    maxCharacters: 10
  };
  const output = shallow(<QueryNameField {...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
