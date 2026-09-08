import { createAction } from '../createAction';

describe('createAction', () => {
  it('Should create a method that yields a properly formatted Redux action', () => {
    const expected = {
      type: 'MOCK_REDUX_ACTION_TYPE',
      payload: 'MOCK REDUX ACTION PAYLOAD',
    };

    const actual = createAction('MOCK_REDUX_ACTION_TYPE')('MOCK REDUX ACTION PAYLOAD');

    expect(actual).toEqual(expected);
  });
});
