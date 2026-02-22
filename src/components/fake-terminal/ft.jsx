import avatar from "../../assets/img/avatar.png";
import ribbon from "../../assets/img/github_ribbon.png";

const Terminal = () => {
  return (
    <div>
      <div id="sidenav">
        <button id="sidenavBtn">&#9776;</button>
        <img id="profilePic" alt="Avatar" src={avatar} />
      </div>

      <div id="container">
        <div id="output"></div>

        <div id="input-line" className="input-line">
          <div id="prompt" className="prompt-color"></div>&nbsp;
          <div>
            <input
              type="text"
              id="cmdline"
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="off"
              spellCheck="false"
              autoFocus
            />
          </div>
        </div>
      </div>

      <a
        id="githubImg"
        target="_blank"
        href="https://github.com/luisbraganca/fake-terminal-website/"
      >
        <img alt="Fork me on GitHub" src={ribbon} />
      </a>
    </div>
  );
};

export default Terminal;
