"use client"

import React from "react";
import { useState } from "react";
import './styles.css';


export default function PasswordGenerator(props: {className: string}){
    const className = props.className;

    const [passwordData, setPasswordData] = useState(String);
    const [passwordLength, setPasswordLength] = useState(Number);
    const [useLowerCase, setUseLowerCase] = useState(Boolean);
    const [useUpperCase, setUseUpperCase] = useState(Boolean);
    const [useSymbols, setUseSymbols] = useState(Boolean);
    const [useNumbers, setUseNumbers] = useState(Boolean);
    const [sucessfulMessage ,setSuccessfulMessage] = useState(String);



    const generatePassword = () => {
        let charset = ""
        let newPassword = ""

        if(useLowerCase) charset += "abcdefghijklmnopqrstuvwxyz";
        if(useUpperCase) charset += "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        if(useNumbers) charset += "0123456789";
        if(useSymbols) charset += "!@#$%^&*()";

        for( let i = 0; i < passwordLength; i++){
            newPassword += charset.charAt(Math.floor(Math.random() * charset.length));
        };
        setPasswordData(newPassword);
    };

    const copyToClipboard = () => {
        const element = document.createElement("textarea");
        element.value = passwordData;
        document.body.appendChild(element);
        element.select();
        document.execCommand("copy");
        document.body.removeChild(element);
        setSuccessfulMessage("Senha copiada para a área de transferência!");
        setTimeout(() => setSuccessfulMessage(""), 2000);
    }
 

    return(
    <div className="pw-Index">

        <div className="pw-Header">
            <h1>Keynest Generator</h1>
            <a>Generate strong passwords for your vault</a>
        </div>

        <div className="pw-Options">

        <div className="pw-Length">
            <label>Password Length:</label>
            <input
            id="ip-length"
            type="number"
            
            min="8"
            max={undefined}
            value={passwordLength}
            placeholder="min: 8 | max: undefined"
            onChange={(e) => setPasswordLength(e.target.value)} required/>
        </div>

        <div className="pw-Checkboxes">
            <input
            id="cb-symbols"
            type="checkbox"
            checked={useSymbols}
            onChange={() => setUseSymbols(!useSymbols)} />
            <label id="cb-symbols">symbols?</label>
            <br/>

            <input
            id="cb-numbers"
            type="checkbox"
            checked={useNumbers}
            onChange={() => setUseNumbers(!useNumbers)} />
            <label id="cb-numbers">numbers?</label>
            <br/>

            <input
            id="cb-lcLetters"
            type="checkbox"
            checked={useLowerCase}
            onChange={() => setUseLowerCase(!useLowerCase)} />
            <label id="cb-lcLetters">lower case letters?</label>
            <br/>

            <input
            id="cb-ucLetters"
            type="checkbox"
            checked={useUpperCase}
            onChange={() => setUseUpperCase(!useUpperCase)}/>
            <label id="cb-ucLetters">upper case letters?</label>
            <br/>
        </div>

        </div>

        <div className="pw-genButton">
            <button
            type="button"
            onClick={generatePassword}>Generate</button>
            {passwordData && (
                <div className="pw-successMessage">
                    <label htmlFor="">Generated password: </label>
                    <input type="text" value={passwordData} readOnly/>
                <button
                    onClick={copyToClipboard}>Copy to Clipboard</button>
                </div>
            )}
            {sucessfulMessage && (
                <p>{sucessfulMessage}</p>
            )}
        </div>
    </div>
)}
