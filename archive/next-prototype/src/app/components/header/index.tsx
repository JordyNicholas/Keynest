'use client'

import React, { useState } from "react";
import './styles.css';

export default function DefaultHeader( props: {img: string, firstMenu: string, secMenu: string, thirdMenu: string }){

    return(
        <div className="hd-Index">
            <div className="hd-Img">
                <img src={props.img}
                    width={50}
                    height={50}
                    alt="Image"/>
            </div>
            <div className="hd-spanList">
                <li><a href= {''}> { props.firstMenu } </a></li>
                <li><a href= {''}> { props.secMenu } </a></li>
                <li><a href= {''}> { props.thirdMenu } </a></li>
            </div>

        
        </div>
    )
}