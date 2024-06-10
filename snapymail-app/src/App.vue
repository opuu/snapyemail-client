<script setup>
import { useMailStore } from './stores/mail.js';
import { ref, watchEffect, onMounted } from 'vue';

const showCompose = ref(false);
const sendingMail = ref(false);
const forwarding = ref(null);



const mailStore = useMailStore();
mailStore.fetchMails('INBOX');
mailStore.fetchFolders();


onMounted(() => {
    watchEffect(() => {
        if (mailStore._active_conversation) {
            document.querySelector('.content')?.scrollTo(0, document.querySelector('.content')?.scrollHeight);
        }
    });
})

setInterval(() => {
    mailStore.fetchMails();
}, 30000); // refresh every 30 seconds

const parseFrom = (from) => {
    const fromName = from.match(/(.*)</) ? from.match(/(.*)</)[1].trim() : from || 'No Name';
    const fromEmail = from.match(/<(.*)>/) ? from.match(/<(.*)>/)[1].trim() : from || 'No Email';
    return { fromName, fromEmail };
};

const sendEmailNow = () => {
    if (sendingMail.value) return;
    sendingMail.value = true;
    mailStore._loading = true;
    const form = document.querySelector('.compose-email form');

    mailStore.sendMail(form.to.value, form.subject.value, form.body.value).then(res => res.text()).then(data => {
        if (data === 'error') {
            alert('Error sending email');
        } else {
            mailStore._sent.push({
                from: "Me",
                to: form.to.value,
                subject: form.subject.value,
                raw: {
                    content: form.body.value
                },
                date: new Date().toLocaleString(),
                sent: true,
                read: true
            })
            showCompose.value = false;
        }
        sendingMail.value = false;
        mailStore._loading = false;
    });
};

const forwardNow = () => {
    if (sendingMail.value) return;
    sendingMail.value = true;
    mailStore._loading = true;
    const form = document.querySelector('.forward-email form');

    mailStore.forwardMail(forwarding.value.messageId, form.to.value).then(res => res.text()).then(data => {
        if (data === 'error') {
            alert('Error sending email');
        } else {
            mailStore._sent.push({
                from: "Me",
                to: form.to.value,
                subject: forwarding.value.subject,
                raw: {
                    content: forwarding.value.raw.content
                },
                date: new Date().toLocaleString(),
                sent: true,
                read: true
            })
            forwarding.value = null;
        }
        sendingMail.value = false;
        mailStore._loading = false;
    });
};

const deleteNow = (mail) => {
    if (window.confirm('Are you sure you want to delete this email?')) {
        if (mail.sent) {
            mailStore._sent = mailStore._sent.filter(m => m !== mail);
            return;
        }
        mailStore.deleteMail(mail.messageId).then(res => res.text()).then(data => {
            if (data === 'error') {
                alert('Error deleting email');
            } else {
                mailStore._is_deleted.push(mail.messageId);
                mailStore.fetchMails();
            }
        });
    }
}


const replyNow = () => {
    showCompose.value = true;
    setTimeout(() => {
        const form = document.querySelector('.compose-email form');
        const activeMail = mailStore._active_conversation;

        form.to.value = activeMail;
    }, 1000);
};

function extractHtml(inputString) {
    // Use a regular expression to match the first HTML tag and everything that follows
    const regex = /<[^>]+>/;
    const match = inputString.match(regex);

    if (match) {
        // Return the substring starting from the match index
        return inputString.substring(inputString.indexOf(match[0]));
    }

    // If no HTML tags are found, return an empty string
    return '';
}

const safehtml = (html) => {
    // remove everything except html and its inner content
    let newhtml = extractHtml(html);

    if (newhtml.length > 0) {
        html = newhtml;
    }

    // remove script tags
    html = html.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '');

    // remove on* events
    html = html.replace(/on\w+="[^"]*"/gi, '');

    // replace all invalid img src with tiny base64 image
    // if url does not start with http or https
    html = html.replace(/<img[^>]*src="(?![http|https])([^"]*)"[^>]*>/gi, '<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=">');

    return html;
};
</script>

<template>
    <main id="snapymail">
        <div class="loading" v-if="mailStore.loading">
            <span class="line"></span>
        </div>
        <header class="header">
            <h1 class="title">
                <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="24px"
                    height="24px" viewBox="0 0 24 24" version="1.1">

                    <defs />
                    <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                        <rect x="0" y="0" width="24" height="24" />
                        <path
                            d="M16,15.6315789 L16,12 C16,10.3431458 14.6568542,9 13,9 L6.16183229,9 L6.16183229,5.52631579 C6.16183229,4.13107011 7.29290239,3 8.68814808,3 L20.4776218,3 C21.8728674,3 23.0039375,4.13107011 23.0039375,5.52631579 L23.0039375,13.1052632 L23.0206157,17.786793 C23.0215995,18.0629336 22.7985408,18.2875874 22.5224001,18.2885711 C22.3891754,18.2890457 22.2612702,18.2363324 22.1670655,18.1421277 L19.6565168,15.6315789 L16,15.6315789 Z"
                            fill="currentColor" />
                        <path
                            d="M1.98505595,18 L1.98505595,13 C1.98505595,11.8954305 2.88048645,11 3.98505595,11 L11.9850559,11 C13.0896254,11 13.9850559,11.8954305 13.9850559,13 L13.9850559,18 C13.9850559,19.1045695 13.0896254,20 11.9850559,20 L4.10078614,20 L2.85693427,21.1905292 C2.65744295,21.3814685 2.34093638,21.3745358 2.14999706,21.1750444 C2.06092565,21.0819836 2.01120804,20.958136 2.01120804,20.8293182 L2.01120804,18.32426 C1.99400175,18.2187196 1.98505595,18.1104045 1.98505595,18 Z M6.5,14 C6.22385763,14 6,14.2238576 6,14.5 C6,14.7761424 6.22385763,15 6.5,15 L11.5,15 C11.7761424,15 12,14.7761424 12,14.5 C12,14.2238576 11.7761424,14 11.5,14 L6.5,14 Z M9.5,16 C9.22385763,16 9,16.2238576 9,16.5 C9,16.7761424 9.22385763,17 9.5,17 L11.5,17 C11.7761424,17 12,16.7761424 12,16.5 C12,16.2238576 11.7761424,16 11.5,16 L9.5,16 Z"
                            fill="currentColor" opacity="0.3" />
                    </g>
                </svg>SnapyEmail
            </h1>
            <div class="actions">
                <button @click="mailStore.fetchMails()">
                    <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="24px"
                        height="24px" viewBox="0 0 24 24" version="1.1">
                        <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                            <rect x="0" y="0" width="24" height="24" />
                            <path
                                d="M8.43296491,7.17429118 L9.40782327,7.85689436 C9.49616631,7.91875282 9.56214077,8.00751728 9.5959027,8.10994332 C9.68235021,8.37220548 9.53982427,8.65489052 9.27756211,8.74133803 L5.89079566,9.85769242 C5.84469033,9.87288977 5.79661753,9.8812917 5.74809064,9.88263369 C5.4720538,9.8902674 5.24209339,9.67268366 5.23445968,9.39664682 L5.13610134,5.83998177 C5.13313425,5.73269078 5.16477113,5.62729274 5.22633424,5.53937151 C5.384723,5.31316892 5.69649589,5.25819495 5.92269848,5.4165837 L6.72910242,5.98123382 C8.16546398,4.72182424 10.0239806,4 12,4 C16.418278,4 20,7.581722 20,12 C20,16.418278 16.418278,20 12,20 C7.581722,20 4,16.418278 4,12 L6,12 C6,15.3137085 8.6862915,18 12,18 C15.3137085,18 18,15.3137085 18,12 C18,8.6862915 15.3137085,6 12,6 C10.6885336,6 9.44767246,6.42282109 8.43296491,7.17429118 Z"
                                fill="currentColor" fill-rule="nonzero" />
                        </g>
                    </svg>
                    Refresh
                </button>
                <button class="compose" @click="showCompose = true">
                    <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="24px"
                        height="24px" viewBox="0 0 24 24" version="1.1">

                        <defs />
                        <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                            <rect x="0" y="0" width="24" height="24" />
                            <path
                                d="M12.2674799,18.2323597 L12.0084872,5.45852451 C12.0004303,5.06114792 12.1504154,4.6768183 12.4255037,4.38993949 L15.0030167,1.70195304 L17.5910752,4.40093695 C17.8599071,4.6812911 18.0095067,5.05499603 18.0083938,5.44341307 L17.9718262,18.2062508 C17.9694575,19.0329966 17.2985816,19.701953 16.4718324,19.701953 L13.7671717,19.701953 C12.9505952,19.701953 12.2840328,19.0487684 12.2674799,18.2323597 Z"
                                fill="currentColor" fill-rule="nonzero"
                                transform="translate(14.701953, 10.701953) rotate(-135.000000) translate(-14.701953, -10.701953) " />
                            <path
                                d="M12.9,2 C13.4522847,2 13.9,2.44771525 13.9,3 C13.9,3.55228475 13.4522847,4 12.9,4 L6,4 C4.8954305,4 4,4.8954305 4,6 L4,18 C4,19.1045695 4.8954305,20 6,20 L18,20 C19.1045695,20 20,19.1045695 20,18 L20,13 C20,12.4477153 20.4477153,12 21,12 C21.5522847,12 22,12.4477153 22,13 L22,18 C22,20.209139 20.209139,22 18,22 L6,22 C3.790861,22 2,20.209139 2,18 L2,6 C2,3.790861 3.790861,2 6,2 L12.9,2 Z"
                                fill="currentColor" fill-rule="nonzero" opacity="0.3" />
                        </g>
                    </svg>

                    Compose
                </button>
                <button class="compose" @click="replyNow">
                    <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="24px"
                        height="24px" viewBox="0 0 24 24" version="1.1">

                        <defs />
                        <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                            <rect x="0" y="0" width="24" height="24" />
                            <path
                                d="M12.2674799,18.2323597 L12.0084872,5.45852451 C12.0004303,5.06114792 12.1504154,4.6768183 12.4255037,4.38993949 L15.0030167,1.70195304 L17.5910752,4.40093695 C17.8599071,4.6812911 18.0095067,5.05499603 18.0083938,5.44341307 L17.9718262,18.2062508 C17.9694575,19.0329966 17.2985816,19.701953 16.4718324,19.701953 L13.7671717,19.701953 C12.9505952,19.701953 12.2840328,19.0487684 12.2674799,18.2323597 Z"
                                fill="currentColor" fill-rule="nonzero"
                                transform="translate(14.701953, 10.701953) rotate(-135.000000) translate(-14.701953, -10.701953) " />
                            <path
                                d="M12.9,2 C13.4522847,2 13.9,2.44771525 13.9,3 C13.9,3.55228475 13.4522847,4 12.9,4 L6,4 C4.8954305,4 4,4.8954305 4,6 L4,18 C4,19.1045695 4.8954305,20 6,20 L18,20 C19.1045695,20 20,19.1045695 20,18 L20,13 C20,12.4477153 20.4477153,12 21,12 C21.5522847,12 22,12.4477153 22,13 L22,18 C22,20.209139 20.209139,22 18,22 L6,22 C3.790861,22 2,20.209139 2,18 L2,6 C2,3.790861 3.790861,2 6,2 L12.9,2 Z"
                                fill="currentColor" fill-rule="nonzero" opacity="0.3" />
                        </g>
                    </svg>

                    Reply
                </button>
            </div>
        </header>
        <div class="container">
            <aside>
                <select v-model="mailStore._selected_folder" @change="mailStore.fetchMails($event.target.value)">
                    <option v-for="(folder, index) in mailStore.folders" :key="index" :value="folder" v-show="folder">
                        {{ folder }}
                    </option>
                </select>

                <div class="emails">
                    <div v-for="(mail, index) in Object.keys(mailStore.mapped_mails)" :key="index"
                        @click="mailStore._active_conversation = mail">
                        <div class="email"
                            :class="{ 'is-unread': !mailStore.is_read.includes(mail), 'is-active': mailStore._active_conversation === mail }">
                            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                                width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                    <rect x="0" y="0" width="24" height="24" />
                                    <circle fill="currentColor" opacity="0.3" cx="12" cy="12" r="10" />
                                    <path
                                        d="M12,11 C10.8954305,11 10,10.1045695 10,9 C10,7.8954305 10.8954305,7 12,7 C13.1045695,7 14,7.8954305 14,9 C14,10.1045695 13.1045695,11 12,11 Z M7.00036205,16.4995035 C7.21569918,13.5165724 9.36772908,12 11.9907452,12 C14.6506758,12 16.8360465,13.4332455 16.9988413,16.5 C17.0053266,16.6221713 16.9988413,17 16.5815,17 L7.4041679,17 C7.26484009,17 6.98863236,16.6619875 7.00036205,16.4995035 Z"
                                        fill="currentColor" opacity="0.3" />
                                </g>
                            </svg>
                            <div class="user">
                                <h3>{{ parseFrom(mail).fromName }}</h3>
                                <span>{{ parseFrom(mail).fromEmail }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </aside>


            <div class="compose-email" v-if="showCompose">
                <span class="close" @click="showCompose = false">
                    <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="24px"
                        height="24px" viewBox="0 0 24 24" version="1.1">
                        <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                            <g transform="translate(12.000000, 12.000000) rotate(-45.000000) translate(-12.000000, -12.000000) translate(4.000000, 4.000000)"
                                fill="currentColor">
                                <rect x="0" y="7" width="16" height="2" rx="1" />
                                <rect opacity="0.3"
                                    transform="translate(8.000000, 8.000000) rotate(-270.000000) translate(-8.000000, -8.000000) "
                                    x="0" y="7" width="16" height="2" rx="1" />
                            </g>
                        </g>
                    </svg>
                </span>
                <h2>Compose Email</h2>
                <form @submit.prevent="sendEmailNow">
                    <input type="text" placeholder="To" required name="to">
                    <input type="text" placeholder="Subject" required name="subject">
                    <textarea placeholder="Message" required name="body"></textarea>
                    <div class="button-group">
                        <button type="submit" :disabled="sendingMail">Send</button>
                    </div>
                </form>
            </div>

            <div class="forward-email" v-if="forwarding">
                <span class="close" @click="forwarding = null">
                    <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="24px"
                        height="24px" viewBox="0 0 24 24" version="1.1">
                        <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                            <g transform="translate(12.000000, 12.000000) rotate(-45.000000) translate(-12.000000, -12.000000) translate(4.000000, 4.000000)"
                                fill="currentColor">
                                <rect x="0" y="7" width="16" height="2" rx="1" />
                                <rect opacity="0.3"
                                    transform="translate(8.000000, 8.000000) rotate(-270.000000) translate(-8.000000, -8.000000) "
                                    x="0" y="7" width="16" height="2" rx="1" />
                            </g>
                        </g>
                    </svg>
                </span>
                <h2>Forward Email</h2>
                <form @submit.prevent="forwardNow">
                    <input type="text" placeholder="To" required name="to">
                    <div class="button-group">
                        <button type="submit" :disabled="sendingMail">Send</button>
                    </div>
                </form>
            </div>

            <section class="content" v-if="mailStore.active_conversation.length > 0">
                <div :class="{ 'email-content email-content--sent': mail.sent, 'email-content email-content--received': !mail.sent }"
                    v-for="(mail, index) in mailStore.active_conversation" :key="index">
                    <div class="email-content-header">
                        {{ parseFrom(mail.from).fromName || 'Me' }}
                    </div>
                    <div class="email-content-content">
                        <h2>{{ mail.subject }}

                            <span class="actions">
                                <button @click="forwarding = mail">
                                    <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                                        width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                        <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                            <rect x="0" y="0" width="24" height="24" />
                                            <path
                                                d="M10.9,2 C11.4522847,2 11.9,2.44771525 11.9,3 C11.9,3.55228475 11.4522847,4 10.9,4 L6,4 C4.8954305,4 4,4.8954305 4,6 L4,18 C4,19.1045695 4.8954305,20 6,20 L18,20 C19.1045695,20 20,19.1045695 20,18 L20,16 C20,15.4477153 20.4477153,15 21,15 C21.5522847,15 22,15.4477153 22,16 L22,18 C22,20.209139 20.209139,22 18,22 L6,22 C3.790861,22 2,20.209139 2,18 L2,6 C2,3.790861 3.790861,2 6,2 L10.9,2 Z"
                                                fill="currentColor" fill-rule="nonzero" opacity="0.3" />
                                            <path
                                                d="M24.0690576,13.8973499 C24.0690576,13.1346331 24.2324969,10.1246259 21.8580869,7.73659596 C20.2600137,6.12944276 17.8683518,5.85068794 15.0081639,5.72356847 L15.0081639,1.83791555 C15.0081639,1.42370199 14.6723775,1.08791555 14.2581639,1.08791555 C14.0718537,1.08791555 13.892213,1.15726043 13.7542266,1.28244533 L7.24606818,7.18681951 C6.93929045,7.46513642 6.9162184,7.93944934 7.1945353,8.24622707 C7.20914339,8.26232899 7.22444472,8.27778811 7.24039592,8.29256062 L13.7485543,14.3198102 C14.0524605,14.6012598 14.5269852,14.5830551 14.8084348,14.2791489 C14.9368329,14.140506 15.0081639,13.9585047 15.0081639,13.7695393 L15.0081639,9.90761477 C16.8241562,9.95755456 18.1177196,10.0730665 19.2929978,10.4469645 C20.9778605,10.9829796 22.2816185,12.4994368 23.2042718,14.996336 L23.2043032,14.9963244 C23.313119,15.2908036 23.5938372,15.4863432 23.9077781,15.4863432 L24.0735976,15.4863432 C24.0735976,15.0278051 24.0690576,14.3014082 24.0690576,13.8973499 Z"
                                                fill="currentColor" fill-rule="nonzero"
                                                transform="translate(15.536799, 8.287129) scale(-1, 1) translate(-15.536799, -8.287129) " />
                                        </g>
                                    </svg>
                                </button>
                                <button @click="deleteNow(mail)">
                                    <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                                        width="24px" height="24px" viewBox="0 0 24 24" version="1.1">
                                        <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                                            <rect x="0" y="0" width="24" height="24" />
                                            <path
                                                d="M6,8 L18,8 L17.106535,19.6150447 C17.04642,20.3965405 16.3947578,21 15.6109533,21 L8.38904671,21 C7.60524225,21 6.95358004,20.3965405 6.89346498,19.6150447 L6,8 Z M8,10 L8.45438229,14.0894406 L15.5517885,14.0339036 L16,10 L8,10 Z"
                                                fill="currentColor" fill-rule="nonzero" />
                                            <path
                                                d="M14,4.5 L14,3.5 C14,3.22385763 13.7761424,3 13.5,3 L10.5,3 C10.2238576,3 10,3.22385763 10,3.5 L10,4.5 L5.5,4.5 C5.22385763,4.5 5,4.72385763 5,5 L5,5.5 C5,5.77614237 5.22385763,6 5.5,6 L18.5,6 C18.7761424,6 19,5.77614237 19,5.5 L19,5 C19,4.72385763 18.7761424,4.5 18.5,4.5 L14,4.5 Z"
                                                fill="currentColor" opacity="0.3" />
                                        </g>
                                    </svg>
                                </button>
                            </span>
                        </h2>
                        <p v-if="!mail.raw">
                            Loading...
                        </p>
                        <p v-else v-html="safehtml(mail.raw.content)">
                        </p>
                    </div>
                    <div class="email-content-meta">
                        <span class="date">
                            {{ new Date(mail.date).toLocaleString() }}
                        </span>
                    </div>
                </div>
            </section>
        </div>
    </main>
</template>

<style lang="scss">
body {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

#snapymail {
    display: flex;
    flex-direction: column;
    font-family: 'Poppins', sans-serif;
    height: 100vh;
    overflow: hidden;

    .compose-email,
    .forward-email {
        display: block;
        padding: 1rem;
        border-radius: 0.5rem;
        margin-bottom: 1rem;
        max-width: 100%;
        min-width: 500px;
        position: fixed;
        bottom: 0;
        right: 0;
        background-color: #fff;
        margin-right: 2rem;
        margin-bottom: 2rem;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);

        .close {
            position: absolute;
            top: 0.5rem;
            right: 0.5rem;
            cursor: pointer;
            color: red;
            transform: scale(1.5);
        }

        h2 {
            font-size: 1.5rem;
            margin: 0;
            margin-bottom: 0.5rem;
        }

        form {
            display: grid;
            grid-template-columns: 1fr;
            grid-gap: 1rem;


            input,
            textarea {
                display: block;
                width: calc(100% - 1rem);
                padding: 0.5rem;
                border: 1px solid #c1c1c1;
                border-radius: 5px;
                font-size: 1rem;

                &::placeholder {
                    color: #c1c1c1;
                }

                &:focus {
                    outline: none;
                    border-color: #007bff;
                }

                &:hover {
                    border-color: #007bff;
                }
            }

            textarea {
                height: 100px;
            }

            button {
                padding: 0.5rem 1rem;
                border: none;
                border-radius: 5px;
                background-color: #007bff;
                color: #fff;
                cursor: pointer;
                transition: background-color 0.3s;
                font-size: 1rem;

                &:hover {
                    background-color: #0067d5
                }
            }

            .button-group {
                display: flex;
                justify-content: flex-end;
                align-items: center;

                button {

                    &:first-child {
                        margin-right: 1rem;
                        width: 80%;
                    }

                    &:last-child {
                        background-color: #dc3545;
                        width: 20%;
                    }
                }
            }
        }
    }

    .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 0.5rem 2rem;
        background-color: #fff;
        z-index: 1;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);

        .title {
            font-size: 1.5rem;
            display: flex;
            align-items: center;


            svg {
                display: block;
                margin-right: 0.5rem;
                color: #007bff;
            }
        }

        .actions {
            button {
                margin-left: 1rem;
                padding: 0.5rem 1rem;
                border: none;
                border-radius: 5px;
                background-color: #007bff;
                color: #fff;
                cursor: pointer;
                transition: background-color 0.3s;
                font-size: 1rem;
                display: inline-flex;
                align-items: center;

                svg {
                    margin-right: 0.5rem;
                }

                &:hover {
                    background-color: #0067d5
                }

                &.compose {
                    background-color: #009750;
                    color: #fff;

                    &:hover {
                        background-color: #005d32
                    }
                }
            }
        }
    }

    .container {
        display: flex;
        flex: 1;
        overflow: hidden;

        aside {
            width: 300px;
            padding: 1rem;
            border-right: 1px solid #c1c1c1;

            select {
                width: 100%;
                padding: 0.5rem;
                margin-bottom: 1rem;

                option {
                    padding: 0.5rem;
                    color: #333;
                    text-transform: capitalize;
                }
            }

            .emails {
                overflow: hidden;
                overflow-y: auto;
                max-height: calc(100vh - 200px);
                display: flex;
                flex-direction: column-reverse;

                .email {
                    display: flex;
                    align-items: center;
                    padding: 0.5rem;
                    cursor: pointer;
                    transition: background-color 0.3s;
                    margin-bottom: 0.2rem;
                    border-radius: 1rem;

                    &.is-unread {
                        background-color: #00ff3c13;
                    }

                    &.is-active {
                        background-color: #007bff13;
                    }

                    &:hover {
                        background-color: #007bff13;
                    }

                    .user {
                        margin-left: 0.5rem;

                        h3 {
                            font-size: 1rem;
                            margin: 0;
                            max-width: 210px;
                            white-space: nowrap;
                            overflow: hidden;
                            text-overflow: ellipsis;
                        }

                        span {
                            font-size: 0.8rem;
                            color: #777;
                            max-width: 210px;
                            white-space: nowrap;
                            overflow: hidden;
                            text-overflow: ellipsis;
                        }
                    }

                    svg {
                        margin-right: 0.5rem;
                        color: #007bff;
                        min-height: 50px;
                        min-width: 50px;
                    }
                }
            }
        }

        .content {
            display: grid;
            flex: 1;
            padding: 1rem;
            overflow: auto;
            overflow-y: scroll;
            max-height: calc(100vh - 100px);

            .email-content {
                padding: 1rem;
                border-radius: 0.5rem;
                margin-bottom: 1rem;
                min-width: 500px;

                &-header {
                    font-size: 0.9rem;
                    margin-bottom: 0.3rem;
                    margin-left: 0.5rem;
                    font-weight: bold;
                    color: gray;
                }

                &-content {
                    background-color: #007bff;
                    padding: 1rem;
                    border-radius: 1rem;
                    color: #fff;
                    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);

                    h2 {
                        display: flex;
                        justify-content: space-between;
                        font-size: 1.5rem;
                        margin: 0;

                        .actions {
                            button {
                                padding: 0.5rem;
                                border: none;
                                border-radius: 5px;
                                background-color: transparent;
                                color: #007bff;
                                cursor: pointer;
                                transition: background-color 0.3s;
                                font-size: 1rem;
                                display: inline-flex;
                                align-items: center;
                                transition: color 0.3s;

                                svg {
                                    margin-right: 0.5rem;
                                }

                                &:hover {
                                    color: #2577ce
                                }

                                &:first-child {
                                    margin-right: 0.5rem;
                                }

                                &:last-child {
                                    color: #dc3545;

                                    &:hover {
                                        color: #c82333
                                    }
                                }
                            }
                        }
                    }

                    p {
                        font-size: 1rem;
                        margin-bottom: 0;
                    }
                }

                &-meta {
                    font-size: 0.8rem;
                    color: #777;
                    margin-top: 0.5rem;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;

                    .date {
                        font-size: 0.8rem;
                        text-align: right;
                        width: 100%;
                    }
                }

                &--sent {
                    justify-self: flex-end;

                    .email-content-content {
                        .actions {
                            button {
                                color: #fff;

                                &:hover {
                                    color: #a2a2a2
                                }

                                &:last-child {
                                    color: #e52033;

                                    &:hover {
                                        color: #c04450
                                    }
                                }
                            }
                        }
                    }
                }

                &--received {
                    justify-self: flex-start;

                    .email-content-content {
                        background-color: #fff;
                        color: #333;
                    }
                }
            }
        }
    }

}

.loading {
    display: flex;
    height: 3px;
    width: 100%;
    position: fixed;
    top: 0;
    left: 0;
    background-color: #007bff;
    z-index: 9999;

    .line {
        background-color: #fff;
        height: 100%;
        width: 0;
        animation: loading 3s infinite;
        position: absolute;
        right: 0;

        @keyframes loading {
            0% {
                width: 10%;
            }

            20% {
                width: 20%;
            }

            40% {
                width: 90%;
            }

            60% {
                width: 40%;
            }

            100% {
                width: 10%;
            }
        }
    }
}
</style>
