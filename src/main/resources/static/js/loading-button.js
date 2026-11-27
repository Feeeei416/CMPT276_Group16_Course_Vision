// Shows that a slow form is working: after one is submitted, every button on the page is turned off,
// so a second refresh can't be started, and the clicked one says "Refreshing…" with a spinner.
// The next page load puts everything back
document.querySelectorAll("form[data-loading]").forEach((form) => {
  form.addEventListener("submit", () => {
    document.querySelectorAll("form[data-loading] button").forEach((button) => {
      button.disabled = true;
    });
    const button = form.querySelector("button[type=submit]");
    button.setAttribute("aria-busy", "true");
    button.textContent = "Refreshing…";
    const spinner = document.createElement("span");
    spinner.className = "spinner";
    spinner.setAttribute("aria-hidden", "true");
    button.prepend(spinner);
  });
});