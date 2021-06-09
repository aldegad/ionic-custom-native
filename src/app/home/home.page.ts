import { Component } from '@angular/core';

import { Plugins } from '@capacitor/core';
const { CustomNativePlugin } = Plugins;

@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
})
export class HomePage {

  constructor() {}

  startBle() {
    CustomNativePlugin.startBle();
  }
  async stopBle() {
    CustomNativePlugin.stopBle();
    /* const res = await CustomNativePlugin.customFunction();
    alert(JSON.stringify(res)); */
  }
}