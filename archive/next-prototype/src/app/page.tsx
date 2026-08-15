import React from "react";
import PasswordGenerator from "./pages/passwordGenerator/page";
import DefaultHeader from "./components/header";
import PasswordManager from "./pages/passwordManager/page";
import './styles.css';

export default async function RootPage() {

  return (      
    <div className="root-div">
      <div className="header-div">
      <DefaultHeader 
        img="https://avatars.githubusercontent.com/u/56373651?v=4"
        firstMenu="Generator"
        secMenu="Vault"
        thirdMenu="About"/>
      </div>

      <div className="body-div">
        <PasswordGenerator className="cssTest"/>
        <PasswordManager/>
      </div>
    </div>
  )
}
