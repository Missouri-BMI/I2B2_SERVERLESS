export function renderCheckbox(props) {
  const footer = document.querySelector(".shepherd-footer");
  if (!footer) {
    return;
  }

  const preferencesLabelText = "don't show this again";
  const markup = `
    <div class="tutorial-preferences">
      <input type="checkbox" class="tutorial-preferences-checkbox">
      <label class="tutorial-preferences-label">${preferencesLabelText}</label>
    </div>
  `;

  const checkContainer = document.createElement("div");
  checkContainer.innerHTML = markup;
  const checkbox = checkContainer.querySelector(
    ".tutorial-preferences-checkbox"
  );
  checkbox.addEventListener(
    "click",
    (event) => {
      props.onShowAgainChange({
        hideTutorial: event.target.checked,
      });
    },
    false
  );
  footer.insertAdjacentElement("afterbegin", checkContainer);
}
