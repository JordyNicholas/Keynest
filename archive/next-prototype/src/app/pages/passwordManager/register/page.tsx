import React from "react";
import { Request, Response } from "express";
import './styles.css'

export default async function RegisterPage() {
    return(
        <div className="pm-Register">
            <form>
                <h1>Login</h1>
                <label>E-mail:</label><input type="email"/><br/>
                <label>Password:</label><input type="password"/><br/>
                <button type="submit">Register</button>
            </form>
        </div>
    )
    
}