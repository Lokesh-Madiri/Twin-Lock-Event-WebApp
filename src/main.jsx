import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.jsx";
import "./index.css";

/**
 * Mount main React app (if you use it for admin panel or extra UI)
 */
const rootElement = document.getElementById("root");

if (rootElement) {
    ReactDOM.createRoot(rootElement).render(
        <React.StrictMode>
            <App />
        </React.StrictMode>
    );
}

/**
 * Optional: Mount React inside terminal hook
 * (Only if you want dynamic React component inside terminal)
 */
const hookElement = document.getElementById("react-hook");

if (hookElement) {
    ReactDOM.createRoot(hookElement).render(
        <div style={{ color: "lime", fontFamily: "monospace" }}>
            {/* Replace this with your real component later */}
        </div>
    );
}